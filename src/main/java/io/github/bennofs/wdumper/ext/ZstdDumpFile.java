package io.github.bennofs.wdumper.ext;

import com.github.luben.zstd.ZstdInputStream;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class ZstdDumpFile extends MwLocalDumpFile {
    public ZstdDumpFile(String filepath) {
        super(filepath);
    }

    @Override
    public InputStream getDumpFileStream() throws IOException {
        if (!this.getPath().toString().contains("zstd")) {
            return super.getDumpFileStream();
        }
        return new ZstdInputStream(new BufferedInputStream(Files.newInputStream(this.getPath(), StandardOpenOption.READ)));
    }
}