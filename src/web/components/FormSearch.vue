<template>
  <form method="get" action="/search" class="search" role="search" aria-label="existing dumps" @submit="handleSubmit($event)">
    <input name="text" type="search" autocomplete="off" class="search__input" placeholder="Search existing dumps" @input="handleInput($event)">
    <input type="submit" class="search__submit" tabindex="-1" alt="Search" value="Search">
  </form>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {defineComponent, ref, watch, watchEffect} from "@vue/composition-api";

    export default defineComponent({
        name: "FormSearch",

        setup(props, ctx) {
            const term = ref("");

            const doSubmit = () => {
                console.log("do submit");
                const newRoute = {
                    "name": "search",
                    "query": {"text": term.value},
                };

                if (ctx.root.$route.name != newRoute.name) {
                    ctx.root.$router.push(newRoute);
                } else {
                    ctx.root.$router.replace(newRoute);
                }
            };

            return {
                handleSubmit(event: Event) {
                    doSubmit();
                    event.preventDefault();
                },
                handleInput(event: Event) {
                    const target = event.target as HTMLInputElement;
                    term.value = target.value;
                    doSubmit();
                }
            }
        }
    })
</script>

<style scoped lang="scss">
  @import "~assets/_base.scss";

  // block: search form
  .search {
    display: flex;
    height: 2rem;
    border: 1px solid transparentize($color-base0, 0.83);
    white-space: nowrap;
    background-color: $color-base100;
    border-radius: 5px;
    overflow: hidden;
    box-shadow: inset 0 0 0.25rem 0.1rem $color-shadow;
  }

  // reset browser styles
  .search__submit, .search__input {
    padding: 0;
    outline: none;
    border: none;
    appearance: none;
    background: none;
  }

  .search__input {
    height: 100%;
    font-size: 1.1rem;
    padding: .2rem 0 .3rem .25rem;
    max-width: 15rem;
  }

  .search__submit {
    height: 100%;
    width: 2.25rem;
    overflow: hidden;
    background: content-box center/80% no-repeat url("~assets/icons/search.svg"), border-box $color-accent90;
    text-indent: -9999px;

    padding: .25rem 0.5rem 0.25rem .5rem;
  }

  .search__input:focus {
    box-shadow: inset 0 0 0 1px $color-accent50;
    border-radius: 5px 0px 0px 5px;
  }

  .search__input:focus + .search__submit, .search__submit:focus {
    background-color: invert($color-accent50);
    filter: invert(1);
    box-shadow: inset 0 0 0 1px invert($color-accent50);
  }
</style>