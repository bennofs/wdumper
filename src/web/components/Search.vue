<template>
    <IconTextField placeholder="search" :img-url="require('@/assets/icons/search.svg')">
    </IconTextField>
</template>

<script lang="ts">
    import Vue from 'vue';
    import IconTextField from "@/components/IconTextField.vue";

    export default Vue.extend({
        name: "Search",
        components: {
            IconTextField
        }
    })
</script>

<style scoped>

</style>