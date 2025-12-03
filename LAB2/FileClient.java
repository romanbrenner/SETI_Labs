import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class FileClient {

    private static final long MAX_FILE_SIZE = 1L << 40; // 1 ТБ
    private static final int MAX_NAME_LENGTH = 4096;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Использование: java FileClient <serverHost> <serverPort> <filePath>");
            return;
        }

        String host = args[0];
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Неверный номер порта: " + args[1]);
            return;
        }

        File file = new File(args[2]);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Файл не найден или не является файлом: " + file.getAbsolutePath());
            return;
        }

        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE) {
            System.err.println("Файл слишком большой (более 1 ТБ): " + fileSize);
            return;
        }

        String fileName = file.getName();
        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length <= 0 || nameBytes.length > MAX_NAME_LENGTH) {
            System.err.println("Имя файла в UTF-8 имеет недопустимую длину: " + nameBytes.length);
            return;
        }

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()));
             FileInputStream fis = new FileInputStream(file)) {

            System.out.printf("Подключено к %s:%d%n", host, port);
            System.out.printf("Отправка файла '%s' (%d байт)%n", fileName, fileSize);

            //отправка длины имени файла
            out.writeInt(nameBytes.length);
            //отправка имени файла
            out.write(nameBytes);
            //отправка размера файла
            out.writeLong(fileSize);
            //отправка содержимого файла
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            //ждем респонс серевера
            boolean success = in.readBoolean();
            if (success) {
                System.out.println("Передача файла завершилась УСПЕШНО.");
            } else {
                System.out.println("Передача файла завершилась С ОШИБКОЙ.");
            }

        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
