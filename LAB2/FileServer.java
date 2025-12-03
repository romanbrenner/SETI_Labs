import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class FileServer {

    private static final long MAX_FILE_SIZE = 1L << 40; // 1 ТБ
    private static final int MAX_NAME_LENGTH = 4096;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Использование: java FileServer <port>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Неверный номер порта: " + args[0]);
            return;
        }

        File uploadsDir = new File("uploads");
        if (!uploadsDir.exists()) {
            if (!uploadsDir.mkdirs()) {
                System.err.println("Не удалось создать директорию 'uploads'");
                return;
            }
        }

        AtomicInteger clientCounter = new AtomicInteger(1);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен. Ожидание подключений на порту " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientId = clientCounter.getAndIncrement();
                System.out.printf("Клиент %d подключился: %s%n",
                        clientId, clientSocket.getRemoteSocketAddress());

                Thread t = new Thread(new ClientHandler(clientSocket, uploadsDir, clientId));
                t.start();
            }

        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String sanitizeFilename(String name) {
        //берем только имя, без пути
        String base = new File(name).getName();
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.isEmpty()) {
            base = "uploaded_file";
        }
        return base;
    }

    private static class ClientHandler implements Runnable {

        private final Socket socket;
        private final File uploadsDir;
        private final int clientId;

        public ClientHandler(Socket socket, File uploadsDir, int clientId) {
            this.socket = socket;
            this.uploadsDir = uploadsDir;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try (Socket s = this.socket;
                 DataInputStream in = new DataInputStream(
                         new BufferedInputStream(s.getInputStream()));
                 DataOutputStream out = new DataOutputStream(
                         new BufferedOutputStream(s.getOutputStream()))) {

                //читаем длину имени файла
                int nameLength = in.readInt();
                if (nameLength <= 0 || nameLength > MAX_NAME_LENGTH) {
                    System.err.printf("Клиент %d: некорректная длина имени файла: %d%n",
                            clientId, nameLength);
                    out.writeBoolean(false);
                    out.flush();
                    return;
                }

                //читаем имя файла
                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);
                String originalFileName = new String(nameBytes, StandardCharsets.UTF_8);
                String safeFileName = FileServer.sanitizeFilename(originalFileName);

                //читаем размер файла
                long fileSize = in.readLong();
                if (fileSize < 0 || fileSize > MAX_FILE_SIZE) {
                    System.err.printf("Клиент %d: некорректный размер файла: %d%n",
                            clientId, fileSize);
                    out.writeBoolean(false);
                    out.flush();
                    return;
                }

                File outFile = new File(uploadsDir, safeFileName);

                //проверяем,что файл точно в пределах uploads (защита от path traversal)
                String uploadsCanonical = uploadsDir.getCanonicalPath();
                String outFileCanonical = outFile.getCanonicalPath();
                if (!outFileCanonical.startsWith(uploadsCanonical + File.separator)) {
                    System.err.printf("Клиент %d: попытка записи за пределы uploads%n", clientId);
                    out.writeBoolean(false);
                    out.flush();
                    return;
                }

                //принимаем файл и считаем скорость
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                long intervalRead = 0;

                long startTime = System.nanoTime();
                long lastReportTime = startTime;
                boolean anyReport = false;

                boolean error = false;

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    while (totalRead < fileSize) {
                        int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) {
                            System.err.printf("Клиент %d: соединение закрыто до конца передачи%n",
                                    clientId);
                            error = true;
                            break;
                        }

                        fos.write(buffer, 0, read);
                        totalRead += read;
                        intervalRead += read;

                        long now = System.nanoTime();
                        long sinceLast = now - lastReportTime;
                        if (sinceLast >= 3_000_000_000L) {
                            double seconds = sinceLast / 1_000_000_000.0;
                            double instantSpeed = intervalRead / seconds;
                            double avgSpeed = totalRead /
                                    ((now - startTime) / 1_000_000_000.0);

                            System.out.printf("Клиент %d: мгновенная скорость = %.2f B/s, " +
                                            "средняя скорость = %.2f B/s%n",
                                    clientId, instantSpeed, avgSpeed);

                            lastReportTime = now;
                            intervalRead = 0;
                            anyReport = true;
                        }
                    }
                } catch (IOException e) {
                    error = true;
                    System.err.printf("Клиент %d: ошибка при записи файла: %s%n",
                            clientId, e.getMessage());
                }

                long endTime = System.nanoTime();

                if (!anyReport) {
                    long elapsedNanos = endTime - startTime;
                    double elapsedSec = Math.max(elapsedNanos / 1_000_000_000.0, 1e-9);
                    double avgSpeed = totalRead / elapsedSec;

                    long sinceLast = endTime - lastReportTime;
                    double intervalSec = sinceLast / 1_000_000_000.0;
                    double instantSpeed;
                    if (intervalSec > 0 && intervalRead > 0) {
                        instantSpeed = intervalRead / intervalSec;
                    } else {
                        instantSpeed = avgSpeed;
                    }

                    System.out.printf("Клиент %d: мгновенная скорость = %.2f B/s, " +
                                    "средняя скорость = %.2f B/s%n",
                            clientId, instantSpeed, avgSpeed);
                } else {
                    long elapsedNanos = endTime - startTime;
                    double elapsedSec = Math.max(elapsedNanos / 1_000_000_000.0, 1e-9);
                    double avgSpeed = totalRead / elapsedSec;
                    System.out.printf("Клиент %d: финальная средняя скорость = %.2f B/s%n",
                            clientId, avgSpeed);
                }

                boolean success = !error && (totalRead == fileSize);

                if (!success) {
                    System.err.printf("Клиент %d: ожидаемый размер = %d, получено = %d%n",
                            clientId, fileSize, totalRead);
                    //можно удалить битый файл
                    if (outFile.exists() && outFile.isFile()) {
                        outFile.delete();
                    }
                } else {
                    System.out.printf("Клиент %d: файл '%s' успешно получен (%d байт)%n",
                            clientId, safeFileName, totalRead);
                }

                //отправляем результат клиенту
                out.writeBoolean(success);
                out.flush();

            } catch (IOException e) {
                System.err.printf("Клиент %d: ошибка: %s%n", clientId, e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                System.out.printf("Клиент %d: соединение закрыто%n", clientId);
            }
        }
    }
}
