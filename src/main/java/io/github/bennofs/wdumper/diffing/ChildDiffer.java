package io.github.bennofs.wdumper.diffing;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;

import java.util.HashSet;
import java.util.Set;

public class ChildDiffer extends Differ {
    private final Differ parent;
    public ChildDiffer(Differ parent) {
        super(new LinkedHashModel(), new LinkedHashModel());
        this.parent = parent;
    }

    public void pullFilter(Resource subject, IRI predicate, Value object) {
        this.a.addAll(parent.a.filter(subject, predicate, object));
        parent.a.remove(subject, predicate, object);
        this.b.addAll(parent.b.filter(subject, predicate, object));
        parent.b.remove(subject, predicate, object);
    }

    public void pullObject(Value object) {
        this.pullFilter(null, null, object);
    }

    public void pullSubject(Resource subject) {
        this.pullFilter(subject, null, null);
    }

    public void pullObjects(String prefix) {
        final Set<Value> objects = new HashSet<>(Sets.union(this.a.objects(), this.b.objects()));
        for (Value object : objects) {
            if (!(object instanceof Resource)) continue;
            final Resource subject = (Resource)object;
            if (!subject.toString().startsWith(prefix)) continue;
            this.pullSubject(subject);
        }
    }

    public void pullBlankObjects() {
        final Set<Resource> subjects = new HashSet<>(Sets.union(this.a.subjects(), this.b.subjects()));
        for (Resource subject : subjects) {
            if (!(subject instanceof BNode)) continue;
            pullObject(subject);
        }
    }
}
