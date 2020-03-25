<template>
  <section class="container">
    <h2 class="title">{{this.title}}</h2>

    <div class="flex">
      <div class="graphic">
        <slot name="graphic"></slot>
      </div>

      <div class="content">
        <slot name="default"></slot>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
    import Vue from 'vue';

    export default Vue.extend({
        name: "Section",
        props: ["title"],
    })
</script>

<style scoped lang="scss">
  @import "~assets/base.scss";

  .title {
    display: block;
    margin-left: 11rem;
    line-height: 2rem;
  }

  .flex {
    display: flex;
  }

  .graphic {
    margin-top: -1rem;
    width: 10rem;
  }

  .content {
    margin-left: 1rem;
    margin-top: 0.5rem;
  }

  .container {
    margin-bottom: 2rem;
  }
</style>