<template>
    <div class="outer">
        <input class="text" :placeholder="placeholder"/>
        <span class="icon-holder">
            <img class="icon" :src="imgUrl" />
        </span>
    </div>
</template>

<script lang="ts">
    import Vue from 'vue';

    export default Vue.extend({
        name: "TextField",
        props: ["placeholder", "imgUrl"],
    })
</script>

<style scoped lang="scss">
    @import "~assets/base.scss";

    .icon-holder {
        height: 100%;
        margin: 0;
        position: absolute;
        top: 0;
        right: 0;
        width: 2rem;
        border-radius: 0 5px 5px 0;

        .icon {
            width: 1rem;
            height: 80%;
            right: 0;
            padding-right: 0.5rem;
            z-index: 1;
            position: absolute;
            bottom: 10%;
        }
    }

    .text {
        // reset browser styles
        outline: 0;
        margin: 0;

        // padding on right is larger, to make space for icon
        padding: 0 1.5rem 0 0.5rem;
        border-radius: 5px;
        box-sizing: border-box;
        height: 100%;
        border: 1px solid transparentize($color-base0, 0.83);
        &:focus {
            box-shadow: 0 0 0 1px $color-accent50;

            & + .icon-holder {
                background-color: $color-accent50;
                .icon {
                    filter: invert(100%);
                }
            }
        }
    }

    .outer {
        white-space: nowrap;
        position: relative;
        height: 2rem;
    }

</style>