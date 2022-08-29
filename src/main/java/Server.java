import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

public class Server implements Runnable{
    protected final List<String> allowedMethods = List.of("GET", "POST");
    protected final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");

    @Override
    public void run() {
        final var threadPool = Executors.newFixedThreadPool(64);
        try (final var serverSocket = new ServerSocket(9999)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.submit(new ConnectingThread(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private class ConnectingThread implements Runnable {
        private final BufferedInputStream in;
        private final BufferedOutputStream out;
        private final Socket socket;

        public ConnectingThread(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = new BufferedOutputStream(socket.getOutputStream());
        }

        private void badRequest(BufferedOutputStream out) throws IOException {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        }

        private void processingRequest() {
            while (true)
                try {
                    final var buffer = new byte[4096];

                    final var read = in.read(buffer);

                    // ищем request line
                    final var requestLineDelimiter = new byte[] {'\r', '\n'};
                    final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                    if (requestLineEnd == -1) {
                        badRequest(out);
                        continue;
                    }

                    // читаем request line
                    final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                    if (requestLine.length == -3) {
                        badRequest(out);
                        continue;
                    }

                    // метод и path
                    final var method = requestLine[0];
                    if (!allowedMethods.contains(method)) {
                        badRequest(out);
                        continue;
                    }
                    final var path = requestLine[1];
                    if (!path.startsWith("/")) {
                        badRequest(out);
                        continue;
                    }

                    // заголовки
                    final var headersDelimiter = new byte[] {'\r', '\n', '\r', '\n'};
                    final var headersStart = requestLineEnd + requestLineDelimiter.length;
                    final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                    if (headersEnd == -1) {
                        badRequest(out);
                        continue;
                    }
                    in.reset();
                    in.skip(headersStart);

                    final var headersByte = in.readNBytes(headersEnd - headersStart);
                    final var headers = Arrays.asList(new String(headersByte).split("\r\n"));
                    final var request = new Request(method, headers, null);
                    System.out.println(request);


                    // тело запроса (только для PUSH)
                    if (!method.equals("GET")) {
                        in.skip(headersDelimiter.length);
                        final var contentLength = extractHeader(headers, "Content-Length");
                        if (contentLength.isPresent()) {
                            final var length = Integer.parseInt(contentLength.get());
                            final var body = new String(in.readNBytes(length));
                        }
                    }

                    // special case for classic
//                    if (path.equals("/classic.html")) {
//                        final var template = Files.readString(path);
//                        final var content = template.replace(
//                                "{time}",
//                                LocalDateTime.now().toString()
//                        ).getBytes();
//                        out.write((
//                                "HTTP/1.1 200 OK\r\n" +
//                                        "Content-Type: " + mimeType + "\r\n" +
//                                        "Content-Length: " + content.length + "\r\n" +
//                                        "Connection: close\r\n" +
//                                        "\r\n"
//                        ).getBytes());
//                        out.write(content);
//                        out.flush();
//                        continue;
//                    }
//
//                    final var length = Files.size(filePath);
                    out.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + 0 + "\r\n" +
                                    "Content-Length: " + 0 + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    //Files.copy(filePath, out);
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        private static int indexOf(byte[] array, byte[] target, int start, int max) {
            for (int i = start; i < max - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue;
                    }
                }
                return i;
            }
            return  -1;
        }

        private static Optional<String> extractHeader(List<String> headers, String header) {
            return headers.stream()
                    .filter(o -> o.startsWith(header))
                    .map(o -> o.substring(o.indexOf(" ")))
                    .map(String::trim)
                    .findFirst();
        }

        @Override
        public void run() {
            processingRequest();
        }
    }
}