<template>
  <div>
    <input ref="property" type="text" placeholder="P31" name="property" :value="props.value.property" @input="update($event)">
    <input ref="value" type="text" placeholder="Q5" name="value" :value="props.value.value" @input="update($event)">
    <input type="text" name="rank" :value="props.value.rank" @input="update($event)">
  </div>
</template>

<script lang="ts">
    import { defineComponent, reactive, computed, onMounted, ref, Ref } from "@vue/composition-api";
    import { completeWikidata } from '~/helpers/complete.ts';

    export default defineComponent({
        name: "StatementPattern",
        props: {
            value: Object,
        },
        setup(props, ctx) {
            function update(event: InputEvent) {
                let target = event.target as HTMLInputElement;
                if (target == null) return;
                const newValue = { [target.name]: target.value, ...props.value };
                ctx.emit('input', newValue);
            }

            const property = ref(null) as Ref<HTMLInputElement> ;
            const value = ref(null) as Ref<HTMLInputElement> ;

            onMounted(() => {
                completeWikidata('property', property.value);
                completeWikidata('item', value.value);
            });

            return { props, update };
        }
    })
</script>

<style scoped lang="scss">
  @import '~assets/_base.scss';

  /* reset the input element styling */
  input {
    outline: none;
    border: none;
    margin: 0;
    padding: 0;
  }

  input[name="property"] {
    background-color: $color-green30;
    color: white;
  }

  input[name="value"] {
    background-color: $color-red30;
    color: white;
  }
</style>

<style lang="scss">
  @import '~assets/_base.scss';

  .autocomplete {
    background: white;
    z-index: 1000;
    font: 0.8rem sans;
    box-sizing: border-box;
    border: 1px solid $color-base70;

    * {
      font: inherit;
    }

    & > div {
      padding: 0 0.5em 0.2em 0.5em;
    }

    .label {
      font-size: 1rem;
    }

    .description {
      opacity: 0.6;
    }

    & > div:hover:not(.group),
    & > div.selected {
      background: $color-accent90;
      cursor: pointer;
    }
  }
</style>