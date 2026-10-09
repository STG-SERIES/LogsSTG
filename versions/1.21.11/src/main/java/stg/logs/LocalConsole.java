package stg.logs;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class LocalConsole {
    private static final int MAX_COMMAND_BYTES = 2048;
    private static final int MAX_HEADER_BYTES = 8192;
    private static final byte[] RESET = "event: reset\ndata: \n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PING = ": ping\n\n".getBytes(StandardCharsets.UTF_8);

    private final LogsPlugin plugin;
    private final LogHub hub;
    private final byte[] page;
    private final ServerSocket server;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Set<Viewer> viewers = ConcurrentHashMap.newKeySet();
    private final Thread acceptor;

    private LocalConsole(LogsPlugin plugin, LogHub hub, byte[] page, ServerSocket server) {
        this.plugin = plugin;
        this.hub = hub;
        this.page = page;
        this.server = server;
        this.acceptor = new Thread(this::acceptLoop, "logsstg-accept");
        this.acceptor.setDaemon(true);
    }

    static LocalConsole start(LogsPlugin plugin, LogHub hub, String host, int port) throws IOException {
        byte[] page;
        try (InputStream in = plugin.getResource("page.html")) {
            if (in == null) {
                throw new IOException("page.html is missing from the jar");
            }
            page = in.readAllBytes();
        }
        ServerSocket server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(host, port));
        } catch (IOException e) {
            server.close();
            throw e;
        }
        LocalConsole console = new LocalConsole(plugin, hub, page, server);
        console.acceptor.start();
        return console;
    }

    void stop() {
        open.set(false);
        for (Viewer viewer : viewers) {
            viewer.wake();
        }
        try {
            server.close();
        } catch (IOException ignored) {
            // Closing the listen socket unblocks accept.
        }
        for (Socket socket : sockets) {
            close(socket);
        }
        acceptor.interrupt();
    }

    private void acceptLoop() {
        while (open.get()) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                break;
            }
            sockets.add(socket);
            Thread client = new Thread(() -> handle(socket), "logsstg-client");
            client.setDaemon(true);
            client.start();
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Request request = Request.read(in);
            socket.setSoTimeout(0);
            route(socket, out, request);
        } catch (IOException ignored) {
            // Client went away or sent a bad request.
        } finally {
            sockets.remove(socket);
            close(socket);
        }
    }

    private void route(Socket socket, OutputStream out, Request request) throws IOException {
        String path = request.path;
        if (path.equals("/")) {
            if (!request.method.equals("GET")) {
                sendStatus(out, 405, "Method Not Allowed");
                return;
            }
            send(out, 200, "OK", "text/html; charset=utf-8", page);
            return;
        }
        if (path.equals("/logs")) {
            if (!request.method.equals("GET")) {
                sendStatus(out, 405, "Method Not Allowed");
                return;
            }
            stream(socket, out);
            return;
        }
        if (path.equals("/command")) {
            if (!request.method.equals("POST")) {
                sendStatus(out, 405, "Method Not Allowed");
                return;
            }
            command(out, request);
            return;
        }
        sendStatus(out, 404, "Not Found");
    }

    private void command(OutputStream out, Request request) throws IOException {
        if (request.body.length > MAX_COMMAND_BYTES) {
            sendStatus(out, 413, "Payload Too Large");
            return;
        }
        String raw = new String(request.body, StandardCharsets.UTF_8).replace("\r", "").trim();
        if (raw.isEmpty() || raw.indexOf('\n') >= 0 || !plugin.runConsole(raw)) {
            sendStatus(out, 400, "Bad Request");
            return;
        }
        sendStatus(out, 204, "No Content");
    }

    private void stream(Socket socket, OutputStream out) throws IOException {
        Viewer viewer = new Viewer(Thread.currentThread());
        viewers.add(viewer);
        try {
            List<String> history = hub.join(viewer);
            out.write((
                    "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/event-stream; charset=utf-8\r\n"
                            + "Cache-Control: no-cache\r\n"
                            + "Connection: keep-alive\r\n"
                            + "Transfer-Encoding: chunked\r\n"
                            + "\r\n"
            ).getBytes(StandardCharsets.US_ASCII));
            Chunked chunked = new Chunked(out);
            chunked.write(RESET);
            for (String line : history) {
                chunked.write(eventBytes(line));
            }
            while (plugin.running() && open.get()) {
                String line = viewer.poll();
                chunked.write(line == null ? PING : eventBytes(line));
            }
            chunked.finish();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            viewers.remove(viewer);
            hub.leave(viewer);
            close(socket);
        }
    }

    private static byte[] eventBytes(String text) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(text.length() + 16);
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i != text.length() && text.charAt(i) != '\n') {
                continue;
            }
            buf.writeBytes("data: ".getBytes(StandardCharsets.UTF_8));
            buf.writeBytes(text.substring(start, i).getBytes(StandardCharsets.UTF_8));
            buf.write('\n');
            start = i + 1;
        }
        buf.write('\n');
        return buf.toByteArray();
    }

    private static void sendStatus(OutputStream out, int status, String reason) throws IOException {
        send(out, status, reason, null, new byte[0]);
    }

    private static void send(OutputStream out, int status, String reason, String type, byte[] body) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n");
        head.append("Cache-Control: no-store\r\n");
        if (type != null) {
            head.append("Content-Type: ").append(type).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private static final class Request {
        final String method;
        final String path;
        final byte[] body;

        private Request(String method, String path, byte[] body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }

        static Request read(InputStream in) throws IOException {
            String header = new String(readHeaders(in), StandardCharsets.ISO_8859_1);
            String[] lines = header.split("\r\n");
            if (lines.length == 0 || lines[0].isEmpty()) {
                throw new IOException("empty request");
            }
            String[] parts = lines[0].split(" ");
            if (parts.length < 2) {
                throw new IOException("bad request line");
            }
            String path = parts[1];
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            Map<String, String> headers = new java.util.HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
            byte[] body = new byte[0];
            if (parts[0].equals("POST")) {
                String lengthText = headers.get("content-length");
                if (lengthText == null) {
                    throw new IOException("missing content-length");
                }
                int length;
                try {
                    length = Integer.parseInt(lengthText);
                } catch (NumberFormatException e) {
                    throw new IOException("bad content-length");
                }
                if (length < 0 || length > MAX_COMMAND_BYTES) {
                    throw new IOException("body too large");
                }
                body = readFully(in, length);
            }
            return new Request(parts[0], path, body);
        }

        private static byte[] readHeaders(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int a = 0;
            int b = 0;
            int c = 0;
            while (buf.size() < MAX_HEADER_BYTES) {
                int d = in.read();
                if (d < 0) {
                    throw new EOFException();
                }
                buf.write(d);
                if (a == '\r' && b == '\n' && c == '\r' && d == '\n') {
                    return buf.toByteArray();
                }
                a = b;
                b = c;
                c = d;
            }
            throw new IOException("headers too large");
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buf = new byte[length];
            int off = 0;
            while (off < length) {
                int n = in.read(buf, off, length - off);
                if (n < 0) {
                    throw new EOFException();
                }
                off += n;
            }
            return buf;
        }
    }

    private static final class Chunked {
        private final OutputStream out;

        private Chunked(OutputStream out) {
            this.out = out;
        }

        void write(byte[] data) throws IOException {
            out.write((Integer.toHexString(data.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(data);
            out.write('\r');
            out.write('\n');
            out.flush();
        }

        void finish() throws IOException {
            out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
    }

    private static final class Viewer implements LogHub.Subscriber {
        private final Thread thread;
        private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(2000);

        private Viewer(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void accept(String line) {
            synchronized (queue) {
                if (!queue.offer(line)) {
                    queue.poll();
                    queue.offer(line);
                }
            }
        }

        String poll() throws InterruptedException {
            return queue.poll(15, TimeUnit.SECONDS);
        }

        void wake() {
            thread.interrupt();
        }
    }
}
