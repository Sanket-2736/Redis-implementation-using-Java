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
            if (opcode == AUX) {
                readString(inputStream);
                readString(inputStream);
                continue;
            }
            if (opcode == SELECT_DB) {
                readLength(inputStream);
                continue;
            }
            if (opcode == RESIZEDB) {
                readLength(inputStream);
                readLength(inputStream);
                continue;
            }
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
            if (opcode == EOF) {
                inputStream.readNBytes(8);
                break;
            }
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
        String key = readString(inputStream);
        String value = readString(inputStream);
        redisData.getData().put(
                key,
                value
        );
        if (expiry != -1) {
            redisData.getExpiry().put(
                    key,
                    expiry
            );
        }
    }

    /**
     * Reads an RDB string (length-prefixed).
     */
    private String readString(
            InputStream inputStream
    ) throws IOException {
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            throw new EOFException();
        }
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
        if (type == 0) {
            return firstByte & 0x3F;
        }
        if (type == 1) {
            int secondByte = inputStream.read();
            if (secondByte == -1) {
                throw new EOFException();
            }
            return ((firstByte & 0x3F) << 8)
                    | secondByte;
        }
        if (type == 2) {
            return readBigEndianUnsignedInt(
                    inputStream
            );
        }
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