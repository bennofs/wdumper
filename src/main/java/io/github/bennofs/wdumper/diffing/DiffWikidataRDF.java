package io.github.bennofs.wdumper.diffing;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DiffWikidataRDF {
    private static class Triple {
        public final byte[] subject;
        public final byte[] predicate;
        public final byte[] object;

        public Triple(byte[] subject, byte[] predicate, byte[] object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Triple triple = (Triple) o;
            return Arrays.equals(subject, triple.subject) &&
                    Arrays.equals(predicate, triple.predicate) &&
                    Arrays.equals(object, triple.object);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(subject);
            result = 31 * result + Arrays.hashCode(predicate);
            result = 31 * result + Arrays.hashCode(object);
            return result;
        }
    }

    private Set<byte[]> triplesDump;
    private Set<byte[]> triplesSerialized;

    public DiffWikidataRDF() {
        this.triplesDump = new HashSet<>();
        this.triplesSerialized = new HashSet<>();
    }

    public void populate(byte[] docDump, byte[] docSerialized) {
        while (true) {
            int idx = ArrayUtils.indexOf(docDump, (byte) 0xa);

        }
    }
}
