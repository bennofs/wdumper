<template>
  <svg class="root" xmlns="http://www.w3.org/2000/svg" viewBox="-101 -101 202 202">
    <!-- arc sectors -->
    <path v-bind:fill="levelColor(level)" v-for="level in levelCount" v-bind:d="sector(level)" />

    <!-- circle boundary for each level -->
    <path stroke="black" stroke-width="1" fill="none" v-for="level in levelCount" v-bind:d="outerCircle(level)"/>

    <!-- ticks for not fully filled levels -->
    <path stroke="black" stroke-width="1" fill="none" v-for="level in levelCount" v-bind:d="ticksPath(level)" />
  </svg>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {defineComponent, reactive, computed, createComponent} from "@vue/composition-api";
    import * as shape from 'd3-shape';
    import * as color from 'd3-color';
    import { path } from 'd3-path';

    /**
     * A multi-level pie chart to display a number that has a large possible range,
     * using different scales for each level of the pie chart.
     */
    export default defineComponent({
        props: {
            /**
             * The number of ticks dividing the full circle that is shown.
             *
             * Default: 36 (each tick corresponds to 10 degrees)
             */
            "ticks": {
                default: 24,
                type: Number,
            },

            /**
             * The percentage of the previous scale represented by a successive inner level.
             */
            "scalePercentagePerLevel": {
                default: 95,
                type: Number,
            },

            /**
             * The value to display in the chart.
             */
            "value": {
                type: Number,
                required: true,
            },

            /**
             * The maximum value that is possible (represents fully filled circles)
             */
            "maxValue": {
                type: Number,
                required: true,
            },
        },

        setup(props, ctx) {
            const levelCount = computed(() => {
                return Math.max(1, Math.ceil(Math.log(props.maxValue) / Math.log(props.scalePercentagePerLevel)));
            });

            const outerRadius = (level: number) => 100 * level / levelCount.value;
            const innerRadius = (level: number) => outerRadius(level - 1);
            const levelScaleMax = (level: number) => Math.pow(props.scalePercentagePerLevel, level);

            const outerCircle = (level: number) => {
                const p = path();
                p.arc(0, 0, outerRadius(level), 0, 2 * Math.PI);
                return p.toString();
            };

            const sector = (level: number) => {
                const angle = Math.min(props.value / levelScaleMax(level), 1) * 2 * Math.PI;

                return shape.arc()({
                    innerRadius: innerRadius(level),
                    outerRadius: outerRadius(level),
                    startAngle: 0,
                    endAngle: angle
                });
            };

            const levelColor = (level: number) => {
                return color.lab("#995500").brighter(levelCount.value - level + 1);
            };

            const ticksPath = (level: number) => {
                const inner = innerRadius(level);
                const outer = outerRadius(level);
                const p = path();

                if (props.value > levelScaleMax(level)) return p.toString();

                for (var angle = 0; angle < 2 * Math.PI - 0.001; angle += 2 * Math.PI / props.ticks) {
                    p.moveTo(Math.sin(angle) * inner, Math.cos(angle) * inner);
                    p.lineTo(Math.sin(angle) * outer, Math.cos(angle) * outer);
                }
                return p.toString();
            };

            return { levelCount, levelColor, sector, outerCircle, ticksPath };
        },
    })
</script>

<style scoped>
 .root {
 }
</style>