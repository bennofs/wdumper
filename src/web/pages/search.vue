<template>
  <div>
    <span>Results for: "{{ term }}"</span>
  </div>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {Route} from 'vue-router';
    import {computed, defineComponent, reactive, Ref, ref, watchEffect} from "@vue/composition-api";

    export default defineComponent({
        name: "search",

        setup(props, ctx) {
            const term = computed((): string => {
                const q = ctx.root.$route.query.text;
                if (typeof q !== "string") {
                    console.warn("Search term must be string");
                    return "";
                }
                return q;
            });

            return { term };
        },
    })
</script>

<style scoped lang="scss">

</style>