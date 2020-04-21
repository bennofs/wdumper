<template>
  <label class="control">
    <input class="input" type="checkbox" v-on="listeners" :checked="props.value"/>
    <span class="control-indicator">
    </span>
    <span class="label">{{label}}</span>
  </label>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {computed, defineComponent} from "@vue/composition-api";

    export default defineComponent({
        name: "BaseCheckbox",
        props: {
            label: String,
            value: Boolean,
        },

        setup(props, ctx) {
            const listeners = computed(() => {
                return Object.assign({}, ctx.listeners, {
                    input(event: Event) {
                        const target = event.target as HTMLInputElement;
                        ctx.emit("input", target.value);
                    }
                });
            });
            return { props, listeners }
        }
    })
</script>

<style scoped lang="scss">
  @import "~assets/_base.scss";

  .input {
    @include sr-only;
  }

  .control-indicator {
    user-select: none;
    display: inline-block;
    width: 1rem;
    height: 1rem;
    border: 1px solid $color-base30;
    vertical-align: middle;
    border-radius: 5px;
    position: relative;
  }

  .input:checked + .control-indicator:before {
    content: "";
    display: block;
    position: absolute;
    width: 1.2rem;
    height: 1.2rem;
    bottom: 0.05rem;
    left: -0.2rem;
    background-image: url("~assets/icons/checkmark.svg");
    background-size: cover;
  }

  .label {
    vertical-align: -0.05em;
  }

  .input:focus + .control-indicator {
    @include mixin-focus($color-brown30);
  }

</style>