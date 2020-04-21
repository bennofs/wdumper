<template>
  <section class="page-section">
    <h2 class="page-section__title">{{props.title}}</h2>
    <div class="page-section__visual">
      <slot name="visual"></slot>
    </div>
    <div class="page-section__main">
      <slot></slot>
    </div>
  </section>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {computed, defineComponent} from "@vue/composition-api";

    export default defineComponent({
        name: "LayoutSection",
        props: {
            title: {
                type: String,
                required: true
            }
        },

        setup(props, ctx) {
            return { props }
        }
    })
</script>

<style scoped lang="scss">
  $visual-width: 12.5rem;

  .page-section {
    margin-bottom: 3rem;
    display: flex;
    flex-flow: row-reverse wrap;
    order: 1;
    justify-content: flex-end;

    &:before {
     content: " ";
     order: 2;
     display: block;
     width: $visual-width;
     height: 0;
   }
  }

  .page-section__title {
    height: 2rem;
    width: calc(100% - #{$visual-width});
    min-width: 30rem;
  }

  .page-section__visual {
    order: 4;
    display: inline-block;
    height: $visual-width;
    margin-right: 1rem;
    width: $visual-width;
  }

  .page-section__main {
    display: block;
    margin-top: 1rem;
    order: 3;
    width: 30rem;
    flex-grow: 1;
  }
</style>