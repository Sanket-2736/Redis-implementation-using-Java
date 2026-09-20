package utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RespUtil {

    // RESP Simple String
    public static void writeSimpleString(
            OutputStream outputStream,
            String value
    ) throws IOException {

        outputStream.write(
                ("+" + value + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    // RESP Bulk String
    public static void writeBulkString(
            OutputStream outputStream,
            String value
    ) throws IOException {

        byte[] data =
                value.getBytes(StandardCharsets.UTF_8);

        outputStream.write(
                ("$" + data.length + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );

        outputStream.write(data);

        outputStream.write(
                "\r\n".getBytes(StandardCharsets.UTF_8)
        );
    }

    // RESP Integer
    public static void writeInteger(
            OutputStream outputStream,
            int value
    ) throws IOException {

        outputStream.write(
                (":" + value + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    // RESP Array
    public static void writeArrayHeader(
            OutputStream outputStream,
            int size
    ) throws IOException {

        outputStream.write(
                ("*" + size + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    // RESP Empty Array
    public static void writeEmptyArray(
            OutputStream outputStream
    ) throws IOException {

        outputStream.write(
                "*0\r\n".getBytes(StandardCharsets.UTF_8)
        );
    }

    // RESP Null Bulk String
    public static void writeNull(
            OutputStream outputStream
    ) throws IOException {

        outputStream.write(
                "$-1\r\n".getBytes(StandardCharsets.UTF_8)
        );
    }

    public static void writeNullArray(OutputStream outputStream) throws IOException{
        outputStream.write(
                "*-1\r\n".getBytes(StandardCharsets.UTF_8)
        );
    }
}