import socket


def encode_command(*args):
    request = f"*{len(args)}\r\n"

    for arg in args:
        arg = str(arg)
        request += f"${len(arg.encode())}\r\n"
        request += f"{arg}\r\n"

    return request.encode()


def read_response(sock):
    first = sock.recv(1)

    if not first:
        return None

    first = first.decode()

    # Simple string
    if first == "+":
        return read_line(sock)

    # Error
    if first == "-":
        return "ERROR: " + read_line(sock)

    # Integer
    if first == ":":
        return int(read_line(sock))

    # Bulk string
    if first == "$":
        length = int(read_line(sock))

        if length == -1:
            return None

        data = read_exact(sock, length)
        sock.recv(2)

        return data.decode()

    # Array
    if first == "*":
        count = int(read_line(sock))

        if count == -1:
            return None

        return [
            read_response(sock)
            for _ in range(count)
        ]

    return first + read_line(sock)


def read_line(sock):
    data = b""

    while True:
        byte = sock.recv(1)

        if byte == b"\r":
            sock.recv(1)
            return data.decode()

        data += byte


def read_exact(sock, length):
    data = b""

    while len(data) < length:
        data += sock.recv(length - len(data))

    return data


def send(sock, *args):
    sock.sendall(encode_command(*args))

    response = read_response(sock)

    print(f">>> {' '.join(map(str, args))}")
    print(f"<<< {response}")
    print()


with socket.create_connection(("localhost", 6379)) as sock:

    send(sock, "PING")
    send(sock, "ECHO", "hello")

    send(sock, "SET", "foo", "200")
    send(sock, "GET", "foo")

    send(sock, "SET", "number", "10")
    send(sock, "INCR", "number")
    send(sock, "GET", "number")

    send(sock, "RPUSH", "mylist", "one", "two", "three")
    send(sock, "LRANGE", "mylist", "0", "-1")
    send(sock, "LLEN", "mylist")
    send(sock, "LPOP", "mylist")

    send(sock, "LPUSH", "mylist", "zero")
    send(sock, "LRANGE", "mylist", "0", "-1")

    send(sock, "TYPE", "mylist")
    send(sock, "TYPE", "foo")

    send(sock, "INFO", "replication")

    send(sock, "WAIT", "0", "1000")

    send(sock, "XADD", "mystream", "*", "name", "Sanket")
    send(sock, "XADD", "mystream", "*", "age", "21")
    send(sock, "XADD", "mystream", "*", "city", "Nashik")

    send(sock, "XADD", "mystream", "1000-*", "foo", "bar")
    send(sock, "XADD", "mystream", "1000-*", "hello", "world")

    send(sock, "XADD", "mystream", "2000-1", "test", "value")