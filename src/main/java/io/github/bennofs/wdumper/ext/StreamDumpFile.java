package io.github.bennofs.wdumper.ext;

import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamDumpFile implements MwDumpFile {
    private final InputStream stream;
    private final String date;

    public StreamDumpFile(String date, InputStream stream) {
        this.stream = stream;
        this.date = date;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProjectName() {
        return "STREAM";
    }

    @Override
    public String getDateStamp() {
        return date;
    }

    @Override
    public DumpContentType getDumpContentType() {
        return DumpContentType.JSON;
    }

    @Override
    public InputStream getDumpFileStream() throws IOException {
        return stream;
    }

    @Override
    public BufferedReader getDumpFileReader() throws IOException {
        return new BufferedReader(new InputStreamReader(stream));
    }

    @Override
    public void prepareDumpFile() throws IOException {
    }
}
