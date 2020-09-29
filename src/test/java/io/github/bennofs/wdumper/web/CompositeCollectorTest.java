package io.github.bennofs.wdumper.web;

import com.samskivert.mustache.Mustache;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompositeCollectorTest {
    private static class Foo {
        public String bar;

        public Foo(String bar) {
            this.bar = bar;
        }
    }

    public static class Timestamp {
        public long unix;

        public Timestamp(long unix) {
            this.unix = unix;
        }
    }

    private static class User {
        public String name;
        public Timestamp created;

        public User(String name, Timestamp created) {
            this.name = name;
            this.created = created;
        }
    }

    public static Optional<Object> fooQuxExtension(Object ctx) {
        if (!(ctx instanceof Foo)) return Optional.empty();

        final Foo foo = (Foo)ctx;
        return Optional.of(Map.of("qux", foo.bar + "qux"));
    }

    public static Optional<Object> fooSpecialExtension(Object ctx) {
        if (!(ctx instanceof Foo)) return Optional.empty();

        final Foo foo = (Foo)ctx;
        if (!foo.bar.equals("special")) return Optional.empty();

        return Optional.of(Map.of("qux", "qux-special"));
    }

    @Test
    void testSimpleField() {
        final var compiler = Mustache.compiler()
                .withCollector(CompositeCollector.builder()
                        .addField(Foo.class, "qux", foo -> foo.bar + "qux")
                        .build()
                );

        final var template = compiler.compile("{{bar}} {{qux}}");
        assertEquals("bar barqux", template.execute(new Foo("bar")));
        assertEquals("wut wutqux", template.execute(new Foo("wut")));
    }

    @Test
    void testSimpleExtension() {
        final var compiler = Mustache.compiler()
                .withCollector(CompositeCollector.builder()
                        .addExtension(Foo.class, foo -> Map.of("qux", foo.bar +  "qux"))
                        .build()
                );

        final var template = compiler.compile("{{bar}} {{qux}}");
        assertEquals("bar barqux", template.execute(new Foo("bar")));
        assertEquals("wut wutqux", template.execute(new Foo("wut")));
    }

    @Test
    void testMultipleExtensionsSameClass() {
        final var compiler = Mustache.compiler()
                .withCollector(CompositeCollector.builder()
                        .addExtension(Foo.class, foo -> Map.of(foo.bar, foo.bar))
                        .addExtension(Foo.class, foo -> Map.of("qux", foo.bar + "qux"))
                        .build()
                );

        final var template = compiler.compile("{{bar}} {{qux}}");
        assertEquals("bar barqux", template.execute(new Foo("bar")));
        assertEquals("qux qux", template.execute(new Foo("qux")));
        assertEquals("bar barqux", template.execute(new Foo("bar")));
    }

    @Test
    void testNestedExtension() {
        final var compiler = Mustache.compiler()
                .withCollector(CompositeCollector.builder()
                        .addExtension(Timestamp.class, timestamp -> Map.of("human", timestamp.unix + " unix"))
                        .build()
                );
        final var template = compiler.compile("{{created.human}} {{#created}}{{human}}{{/created}}");
        assertEquals("42 unix 42 unix", template.execute(new User("foo", new Timestamp(42))));
    }

    @Test
    void testNestedIndirectExtension() {
        final var compiler = Mustache.compiler()
                .withCollector(CompositeCollector.builder()
                        .addExtension(Timestamp.class, timestamp -> Map.of("human", timestamp.unix + " unix"))
                        .addField(Foo.class, "user", foo -> new User(foo.bar, new Timestamp(42)))
                        .build()
                );
        final var template = compiler.compile("{{user.created.human}} {{#user.created}}{{human}}{{/user.created}}");
        assertEquals("42 unix 42 unix", template.execute(new Foo("foo")));
    }

    @Test
    void testOptional() {
        final var compiler = Mustache.compiler().withCollector(CompositeCollector.builder().build());
        final var template = compiler.compile("{{#value}}{{.}}{{/value}}{{^value}}empty{{/value}}");


        assertEquals("test", template.execute(Map.of("value", Optional.of("test"))));
        assertEquals("empty", template.execute(Map.of("value", Optional.empty())));
    }
}
