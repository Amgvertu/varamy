package info.prorabka.varamy.exporter;

public class ExportRunner {
    public static void main(String[] args) {
        // Path to Spring Boot project root
        String projectPath = "D:\\KatokPro\\varamy";

        // If path passed as argument, use it
        if (args.length > 0) {
            projectPath = args[0];
        }

        System.out.println("=== Starting Spring Boot project export ===");
        System.out.println("Project path: " + projectPath);
        System.out.println();

        // Basic export (Java files + settings)
        CodeExporter.exportAll(projectPath);

        System.out.println();
        System.out.println("=== Export completed ===");
        System.out.println("Files saved:");
        System.out.println("  - " + projectPath + "\\allClasses.txt");
        System.out.println("  - " + projectPath + "\\settings.txt");
    }
}
