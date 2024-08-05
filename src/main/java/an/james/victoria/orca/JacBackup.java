package an.james.victoria.orca;

import com.azure.storage.blob.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class JacBackup {

    public static void main(String[] args) {
        // MySQL credentials
        String host = "jamesan-db.mysql.database.azure.com";
        String user = "javacoffee";
        String password = System.getenv("DB_PASS"); // jajva
        String database = "jac";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        String timestamp = dtf.format(LocalDateTime.now());
        String fullBackupFile = "/tmp/full_backup_" + timestamp + ".sql";
        String dataBackupFile = "/tmp/data_backup_" + timestamp + ".sql";

        try {
            // Run mysqldump command for full backup
            ProcessBuilder fullBackupBuilder = new ProcessBuilder(
                "mysqldump", "-h", host, "-u", user, "-p" + password, "--databases", database, "-r", fullBackupFile,
                "--complete-insert", "--skip-add-drop-table", "--add-drop-database", "--set-gtid-purged=OFF"
            );
            fullBackupBuilder.redirectErrorStream(true);
            Process fullBackupProcess = fullBackupBuilder.start();
            int fullBackupExitCode = fullBackupProcess.waitFor();
            
            // Capture and log mysqldump output for full backup
            logProcessOutput(fullBackupProcess);

            if (fullBackupExitCode != 0) {
                throw new RuntimeException("Full backup mysqldump command failed with exit code " + fullBackupExitCode);
            }

            // Run mysqldump command for data-only backup
            ProcessBuilder dataBackupBuilder = new ProcessBuilder(
                "mysqldump", "-h", host, "-u", user, "-p" + password, "--no-create-info", "--complete-insert", database, "-r", dataBackupFile
            );
            dataBackupBuilder.redirectErrorStream(true);
            Process dataBackupProcess = dataBackupBuilder.start();
            int dataBackupExitCode = dataBackupProcess.waitFor();
            
            // Capture and log mysqldump output for data-only backup
            logProcessOutput(dataBackupProcess);

            if (dataBackupExitCode != 0) {
                throw new RuntimeException("Data-only backup mysqldump command failed with exit code " + dataBackupExitCode);
            }

            // Debugging: Set the connection string directly
            String connectStr = System.getenv("AZURE_STORAGE");

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectStr).buildClient();
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("mysql-backup");

            // Create container if it does not exist
            if (!containerClient.exists()) {
                containerClient.create();
            }

            // Upload full backup
            BlobClient fullBlobClient = containerClient.getBlobClient("full_backup_" + timestamp + ".sql");
            Path fullPath = Paths.get(fullBackupFile);
            fullBlobClient.uploadFromFile(fullPath.toString(), true);

            // Upload data-only backup
            BlobClient dataBlobClient = containerClient.getBlobClient("data_backup_" + timestamp + ".sql");
            Path dataPath = Paths.get(dataBackupFile);
            dataBlobClient.uploadFromFile(dataPath.toString(), true);

            System.out.println("Full backup successful and uploaded as full_backup_" + timestamp + ".sql");
            System.out.println("Data-only backup successful and uploaded as data_backup_" + timestamp + ".sql");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            System.out.println("Output:");
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            System.out.println("Error:");
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
        }
    }
}