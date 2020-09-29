package io.github.bennofs.wdumper.web;

import com.google.common.collect.ImmutableList;
import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A collector for mustache that extends the default collector to allow an arbitrary list
 * of additional collectors to add more variables.
 * <p>
 * Mustache uses the collector to turn objects into a form suitable for template substitution.
 * We would like to be able to add additional computed properties to objects, without modifying them.
 * For example, we want to customize the mapping of the {@code Timestamp} type to include fields like {@code since}
 * or {@code instant}. Since we want to have multiple of these extension, we need this composite collector which
 * merges them.
 * <p>
 * For usage examples, see the tests in {@code CompositeCollectorTest.java}
 */
public class CompositeCollector extends DefaultCollector {
    @FunctionalInterface
    public interface Extension {
        /**
         * Apply the extension to the given context.
         * This method should check if the extension can resolve the specified field name
         * for the given object and return a getter if that's the case.
         * <p>
         * /Warning/: because jmustache internally caches fetchers based on object class and field name,
         * this function should NOT inspect the actual values of the ctx object to decide whether
         * it can resolve the field or not. The set of fields that are successfully resolved (return a non-empty optional)
         * should only depend on the class of the context object.
         *
         * @param collector The collector for resolving subfields
         * @param ctx       An example object for which to resolve fields.
         *                  Only use the class to decide if this extension applies
         * @param name      The name of the field to lookup
         * @return A variable fetcher to extract the field for all other instances of the same class
         */
        Optional<Mustache.VariableFetcher> apply(Mustache.Collector collector, Object ctx, String name);
    }

    private final ImmutableList<Extension> extensions;

    /**
     * Construct a new composite collector from a list of extensions.
     * <p>
     * Each extension is a function that takes an object and a field name and may optionally return a new object.
     * During template substitution, if a variable to be looked up is not found in the originally context,
     * the extension functions are evaluated for this context one after another. If any of the extension functions
     * returns a value, that value is used as the result of the lookup.
     * <p>
     * /Note/: Because jmustache internally caches the resolver, whether the extension function returns an empty
     * optional or not should only depend on the *class* of the object and the name of the field.
     *
     * @param extensions List of extensions
     */
    CompositeCollector(ImmutableList<Extension> extensions) {
        this.extensions = extensions;
    }


    @Override
    public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
        final Mustache.VariableFetcher fetcher = super.createFetcher(ctx, name);
        if (fetcher != null) return fetcher;

        final List<Mustache.VariableFetcher> fetchers = extensions.stream()
                .flatMap(e -> e.apply(this, ctx, name).stream())
                .collect(Collectors.toUnmodifiableList());

        // no fetcher found
        if (fetchers.isEmpty()) return null;

        // return a composite fetcher
        // we must check all fetchers every time, since some fetchers only know at runtime
        // whether they are applicable or not (such as the fetcher for a Map)
        return (runtimeCtx, runtimeName) -> {
            for (Mustache.VariableFetcher runtimeFetcher : fetchers) {
                final Object value = runtimeFetcher.get(runtimeCtx, runtimeName);
                if (value != Template.NO_FETCHER_FOUND) return value;
            }
            return Template.NO_FETCHER_FOUND;
        };
    }

    public static CompositeCollectorBuilder builder() {
        return new CompositeCollectorBuilder();
    }

    @Override
    public Iterator<?> toIterator(Object value) {
        if (value instanceof Optional<?>) {
            final Optional<?> optional = (Optional<?>)value;
            if (optional.isEmpty()) {
                return Collections.emptyIterator();
            }

            return List.of(optional.get()).iterator();
        }

        return super.toIterator(value);
    }

    /**
     * A safe interface for adding extensions to types which behave well with caching.
     */
    public static class CompositeCollectorBuilder {
        private final ImmutableList.Builder<Extension> extensions;

        public CompositeCollectorBuilder() {
            this.extensions = ImmutableList.builder();
        }

        public CompositeCollector build() {
            return new CompositeCollector(this.extensions.build());
        }

        /**
         * Add an extension for all instances of a given class.
         *
         * The extension is defined by a function that returns an object whose fields are merged with the fields
         * of the original class. For example, you could return a Map that adds additional fields.
         *
         * @param cls The class to extend
         * @param extension Function to return additional context for instances of that class
         * @param <T> Class type to extend
         */
        public <T> CompositeCollectorBuilder addExtension(Class<T> cls, Function<T, Object> extension) {
            this.extensions.add((collector, ctx, name) -> {
                if (!(cls.isInstance(ctx))) return Optional.empty();

                final Object base = extension.apply(cls.cast(ctx));
                final Mustache.VariableFetcher baseFetcher = collector.createFetcher(base, name);
                if (baseFetcher == null) return Optional.empty();

                return Optional.of((obj, _name) -> {
                    // the caching in jmustache uses _name as part of the cache key,
                    // so the fetcher should never be called with a different name
                    assert name.equals(_name);

                    return baseFetcher.get(extension.apply(cls.cast(obj)), name);
                });
            });
            return this;
        }

        /**
         * Add a single field to all instances of a class.
         *
         * @param cls Class to add the field to
         * @param name Name of the field to add
         * @param getter The getter for the field.
         * @param <T> Class type to extend
         */
        public <T> CompositeCollectorBuilder addField(Class<T> cls, String name, Function<T, Object> getter) {
            this.extensions.add(((collector, ctx, fieldName) -> {
                if (!fieldName.equals(name)) return Optional.empty();

                return Optional.of((obj, _name) -> {
                    // jmustache caches based on name and type, so class and name
                    // should be equal at this point
                    assert name.equals(_name);
                    final T instance = cls.cast(obj);

                    return getter.apply(instance);
                });
            }));
            return this;
        }
    }
}
