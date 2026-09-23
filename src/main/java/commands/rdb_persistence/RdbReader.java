package commands.rdb_persistence;

import storage.RedisData;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RdbReader {

    private static final int STRING_VALUE = 0x00;

    private static final int AUX = 0xFA;
    private static final int RESIZEDB = 0xFB;
    private static final int EXPIRE_TIME_MS = 0xFC;
    private static final int EXPIRE_TIME_SECONDS = 0xFD;
    private static final int SELECT_DB = 0xFE;
    private static final int EOF = 0xFF;

    private final String dir;
    private final String dbfilename;

    public RdbReader(String dir, String dbfilename) {
        this.dir = dir;
        this.dbfilename = dbfilename;
    }

    public void load(RedisData redisData) {

        Path rdbPath = Path.of(dir, dbfilename);

        // No RDB file = empty database
        if (!Files.exists(rdbPath)) {
            return;
        }

        try (
                InputStream inputStream =
                        new BufferedInputStream(
                                Files.newInputStream(rdbPath)
                        )
        ) {
            readHeader(inputStream);
            readRdb(inputStream, redisData);

        } catch (IOException e) {
            System.out.println(
                    "Error reading RDB file: " + e.getMessage()
            );
        }
    }

    private void readHeader(
            InputStream inputStream
    ) throws IOException {

        byte[] header = inputStream.readNBytes(9);

        if (header.length != 9) {
            throw new IOException("Invalid RDB header");
        }

        String headerString =
                new String(
                        header,
                        StandardCharsets.US_ASCII
                );

        if (!headerString.equals("REDIS0011")) {
            throw new IOException(
                    "Invalid RDB header: " + headerString
            );
        }
    }

    private void readRdb(
            InputStream inputStream,
            RedisData redisData
    ) throws IOException {

        while (true) {

            int opcode = inputStream.read();

            if (opcode == -1) {
                break;
            }

            /*
             * Metadata
             *
             * FA
             * [length][key]
             * [length][value]
             */
            if (opcode == AUX) {

                readString(inputStream);
                readString(inputStream);

                continue;
            }

            /*
             * Select database
             *
             * FE
             * [db number]
             */
            if (opcode == SELECT_DB) {

                readLength(inputStream);

                continue;
            }

            /*
             * Database hash table sizes
             *
             * FB
             * [database size]
             * [expiry size]
             */
            if (opcode == RESIZEDB) {

                readLength(inputStream);
                readLength(inputStream);

                continue;
            }

            /*
             * Expiry in milliseconds
             *
             * FC
             * [8 byte little endian timestamp]
             * [value type]
             * [key]
             * [value]
             */
            if (opcode == EXPIRE_TIME_MS) {

                long expiry =
                        readLittleEndianLong(inputStream);

                int valueType =
                        inputStream.read();

                readKeyValue(
                        inputStream,
                        valueType,
                        expiry,
                        redisData
                );

                continue;
            }

            /*
             * Expiry in seconds
             *
             * FD
             * [4 byte little endian timestamp]
             * [value type]
             * [key]
             * [value]
             */
            if (opcode == EXPIRE_TIME_SECONDS) {

                long expirySeconds =
                        readLittleEndianUnsignedInt(
                                inputStream
                        );

                long expiryMilliseconds =
                        expirySeconds * 1000L;

                int valueType =
                        inputStream.read();

                readKeyValue(
                        inputStream,
                        valueType,
                        expiryMilliseconds,
                        redisData
                );

                continue;
            }

            /*
             * End of RDB
             *
             * FF
             * [8 byte checksum]
             */
            if (opcode == EOF) {

                inputStream.readNBytes(8);

                break;
            }

            /*
             * Normal key/value entry.
             *
             * First byte is the value type.
             *
             * For this stage we only support:
             *
             * 00 = string
             */
            int valueType = opcode;

            readKeyValue(
                    inputStream,
                    valueType,
                    -1,
                    redisData
            );
        }
    }

    private void readKeyValue(
            InputStream inputStream,
            int valueType,
            long expiry,
            RedisData redisData
    ) throws IOException {

        if (valueType != STRING_VALUE) {

            throw new IOException(
                    "Unsupported RDB value type: "
                            + valueType
            );
        }

        /*
         * Key is a length-prefixed string.
         */
        String key = readString(inputStream);

        /*
         * Value is also a length-prefixed string.
         */
        String value = readString(inputStream);

        /*
         * RedisData stores string values here.
         */
        redisData.getData().put(
                key,
                value
        );

        /*
         * Store expiry only when the entry has one.
         */
        if (expiry != -1) {

            redisData.getExpiry().put(
                    key,
                    expiry
            );
        }
    }

    /**
     * Reads an RDB string.
     *
     * For this stage we only need normal
     * length-prefixed strings.
     */
    private String readString(
            InputStream inputStream
    ) throws IOException {

        int firstByte = inputStream.read();

        if (firstByte == -1) {
            throw new EOFException();
        }

        /*
         * Top two bits determine the encoding.
         *
         * 00 -> 6-bit length
         * 01 -> 14-bit length
         * 10 -> 32-bit length
         * 11 -> special encoding
         */
        int encoding =
                (firstByte & 0xC0) >> 6;

        if (encoding == 3) {

            throw new IOException(
                    "Special string encoding is not supported"
            );
        }

        long length =
                readLengthAfterFirstByte(
                        inputStream,
                        firstByte
                );

        if (length < 0) {
            throw new IOException(
                    "Invalid string length"
            );
        }

        byte[] bytes =
                inputStream.readNBytes(
                        Math.toIntExact(length)
                );

        if (bytes.length != length) {
            throw new EOFException();
        }

        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }

    private long readLength(
            InputStream inputStream
    ) throws IOException {

        int firstByte = inputStream.read();

        if (firstByte == -1) {
            throw new EOFException();
        }

        return readLengthAfterFirstByte(
                inputStream,
                firstByte
        );
    }

    private long readLengthAfterFirstByte(
            InputStream inputStream,
            int firstByte
    ) throws IOException {

        int type =
                (firstByte & 0xC0) >> 6;

        /*
         * 00xxxxxx
         *
         * Length = lower 6 bits
         */
        if (type == 0) {

            return firstByte & 0x3F;
        }

        /*
         * 01xxxxxx
         * yyyyyyyy
         *
         * 14-bit big-endian length
         */
        if (type == 1) {

            int secondByte = inputStream.read();

            if (secondByte == -1) {
                throw new EOFException();
            }

            return ((firstByte & 0x3F) << 8)
                    | secondByte;
        }

        /*
         * 10xxxxxx
         *
         * Next 4 bytes contain
         * a 32-bit big-endian length.
         */
        if (type == 2) {

            return readBigEndianUnsignedInt(
                    inputStream
            );
        }

        /*
         * 11xxxxxx
         *
         * Special encoding.
         */
        return -1;
    }

    private long readLittleEndianUnsignedInt(
            InputStream inputStream
    ) throws IOException {

        int b0 = inputStream.read();
        int b1 = inputStream.read();
        int b2 = inputStream.read();
        int b3 = inputStream.read();

        if (
                b0 == -1 ||
                        b1 == -1 ||
                        b2 == -1 ||
                        b3 == -1
        ) {
            throw new EOFException();
        }

        return (b0 & 0xFFL)
                | ((b1 & 0xFFL) << 8)
                | ((b2 & 0xFFL) << 16)
                | ((b3 & 0xFFL) << 24);
    }

    private long readLittleEndianLong(
            InputStream inputStream
    ) throws IOException {

        long result = 0;

        for (int i = 0; i < 8; i++) {

            int value = inputStream.read();

            if (value == -1) {
                throw new EOFException();
            }

            result |=
                    (long) (value & 0xFF)
                            << (8 * i);
        }

        return result;
    }

    private long readBigEndianUnsignedInt(
            InputStream inputStream
    ) throws IOException {

        int b0 = inputStream.read();
        int b1 = inputStream.read();
        int b2 = inputStream.read();
        int b3 = inputStream.read();

        if (
                b0 == -1 ||
                        b1 == -1 ||
                        b2 == -1 ||
                        b3 == -1
        ) {
            throw new EOFException();
        }

        return ((b0 & 0xFFL) << 24)
                | ((b1 & 0xFFL) << 16)
                | ((b2 & 0xFFL) << 8)
                | (b3 & 0xFFL);
    }
}