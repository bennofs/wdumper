<template>
    <div>
        <h2>How it works</h2>
        <p>
            This tool provides a simple interface to create customized RDF dumps from Wikidata.
            It is based on <a href="https://github.com/Wikidata/Wikidata-Toolkit">Wikidata Toolkit</a>.
        </p>
        <p>
            The dumping process logically consists of two steps. First, the entities to export are determined.
            Those entities are then exported according to the rules.
            This step uses the rules for statements to determine what to export for each statement of the selected
            entity.
            In addition to the statements, you can also control what other information is exported for the selected
            entity, such as labels.
        </p>
        <p>
            When you submit a request for a dump, it is added to the queue. You can view
            the current queue status <a href="https://tools.wmflabs.org/wdumps/status">here</a>.
        </p>


        <h2>How to help</h2>
        <p>
            The source code for the application is <a href="https://github.com/bennofs/wdumper">hosted on GitHub</a>.
            Feel free to create issues for any bugs you discover or features you like to see implemented.
            Pull requests are of course also appreciated!
        </p>
    </div>
</template>

<script lang="ts">
    import Vue from 'vue';

    export default Vue.extend({
        meta: {
            "title": "About",
        },
    })
</script>