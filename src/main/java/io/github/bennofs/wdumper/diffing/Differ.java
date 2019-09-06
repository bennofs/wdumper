package io.github.bennofs.wdumper.diffing;

import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Differ {
    final Model a;
    final Model b;

    public Differ(Model a, Model b) {
        this.a = a;
        this.b = b;
    }

    private static <T> void requireUnique(Collection<T> collection) {
        if (collection.size() > 1) {
            throw new IllegalArgumentException("non-unique result");
        }
    }

    private static <T> T requireOne(Collection<T> a, Collection<T> b) {
        requireUnique(a);
        requireUnique(b);
        final Set<T> result = Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet());
        if (result.size() < 1) {
            throw new IllegalArgumentException("no result");
        }
        if (result.size() > 1) {
            throw new IllegalArgumentException("mismatching result");
        }
        return result.iterator().next();
    }

    public Model getA() {
        return this.a.unmodifiable();
    }

    public Model getB() {
        return this.b.unmodifiable();
    }

    public List<ChildDiffer> extractSubjects(String prefix) {
        final ArrayList<ChildDiffer> components = new ArrayList<>();
        final Set<Resource> subjects = new HashSet<>(Sets.union(a.subjects(), b.subjects()));
        for (Resource subject : subjects) {
            if (!subject.toString().startsWith(prefix)) continue;

            final ChildDiffer component = new ChildDiffer(this);
            component.pullSubject(subject);
            component.pullObject(subject);
            components.add(component);
        }

        return components;
    }

    public void eliminateCommon() {
        final Set<Statement> toRemove = Sets.intersection(a, b).immutableCopy();
        this.a.removeAll(toRemove);
        this.b.removeAll(toRemove);
    }

    public Set<IRI> eliminatePredicatesOnlyA() {
        final Set<IRI> predicates = Sets.difference(a.predicates(), b.predicates()).immutableCopy();
        for (IRI predicate : predicates) {
            a.remove(null, predicate, null);
        }
        return predicates;
    }

    public Set<IRI> eliminatePredicatesOnlyB() {
        final Set<IRI> predicates = Sets.difference(b.predicates(), a.predicates()).immutableCopy();
        for (IRI predicate : predicates) {
            b.remove(null, predicate, null);
        }
        return predicates;
    }

    public ChildDiffer extractObjects(String s) {
        ChildDiffer child = new ChildDiffer(this);
        final Set<Value> objects = new HashSet<>(Sets.union(this.a.objects(), this.b.objects()));
        for (Value object : objects) {
            if (!(object instanceof Resource)) continue;
            final Resource subject = (Resource)object;
            if (!subject.toString().startsWith(s)) continue;
            child.pullObject(object);
        }
        return child;
    }

    private Set<BNode> getBNodes(Model x) {
        return x.stream().flatMap(stmt -> {
            if (stmt.getObject() instanceof BNode) {
                return Stream.of((BNode)stmt.getObject());
            }

            if (stmt.getSubject() instanceof BNode) {
                return Stream.of((BNode)stmt.getSubject());
            }

            return Stream.empty();
        }).collect(Collectors.toSet());
    }

    public void replaceResource(Resource from, Resource to) {
        replaceResource(this.a, from, to);
        replaceResource(this.b, from, to);
    }

    private static void replaceResource(Model m, Resource from, Resource to) {
        Model related = findRelated(m, from);
        m.removeAll(related);
        for (Statement stmt : related) {
            m.add(
                    stmt.getSubject().equals(from) ? to : stmt.getSubject(),
                    stmt.getPredicate(),
                    stmt.getObject().equals(from) ? to : stmt.getObject()
            );
        }
    }

    public static Model findRelated(Model from, Resource x) {
        final Model result = new LinkedHashModel();
        result.addAll(from.filter(x, null, null));
        result.addAll(from.filter(null, null, x));
        return result;
    }

    public static Model findPattern(Model m, Statement s) {
        return m.filter(
                s.getSubject() instanceof BNode ? null : s.getSubject(),
                s.getPredicate(),
                s.getObject() instanceof BNode ? null : s.getObject()
        );
    }

    public static boolean samePattern(Statement a, Statement b) {
        return a.getPredicate().equals(b.getPredicate()) &&
                (a.getSubject() instanceof BNode ? b.getSubject() instanceof BNode : a.getSubject().equals(b.getSubject())) &&
                (a.getObject() instanceof BNode ? b.getObject() instanceof BNode : a.getObject().equals(b.getObject()));
    }

    public void unifyBNodes() {
        for (BNode bnode : getBNodes(a)) {
            Model relatedA = findRelated(this.a, bnode);
            if (relatedA.size() != 1) continue;

            Model candidates = findPattern(this.b, relatedA.iterator().next());
            for (BNode candidate : getBNodes(candidates)) {
                Model relatedB = findRelated(this.b, candidate);
                if (relatedB.size() != 1) continue;
                if (samePattern(relatedA.iterator().next(), relatedB.iterator().next())) {
                    replaceResource(this.b, candidate, bnode);
                }
            }
        }
    }
}
