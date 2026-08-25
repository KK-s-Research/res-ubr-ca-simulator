package in.ac.caluniv.cse.ubrca;

import in.ac.caluniv.cse.ubrca.artifact.TaskInputDatasetWriter;
import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskInputDatasetWriterTest {
    @TempDir
    Path temp;

    @Test
    void exportsCompressedInputsAndManifest() throws Exception {
        ExperimentConfig config = ExperimentConfig.defaults(temp).withScale(2, 8);
        Path output = temp.resolve("inputs");
        new TaskInputDatasetWriter().write(config, 1, true, output);

        assertTrue(Files.exists(output.resolve("manifest.csv")));
        try (Stream<Path> files = Files.list(output)) {
            assertEquals(6, files
                    .filter(path -> path.getFileName().toString().endsWith(".csv.gz"))
                    .count());
        }

        Path defaults = output.resolve("default_moderate_interval_300.csv.gz");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(defaults)),
                StandardCharsets.UTF_8))) {
            assertTrue(reader.readLine().startsWith("scenario,repetition_index,seed"));
            assertTrue(reader.readLine().startsWith("\"default_moderate_interval_300\",0,"));
        }
    }
}
