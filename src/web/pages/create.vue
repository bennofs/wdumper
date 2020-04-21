<template>
    <div>
      <LayoutSection class="entity-selection" :title='"Step 1: Select entities to export"'>
        <template v-slot:visual v-bind:value="this.entityCount">
          <MultiLevelPieChart class="chart-entity-count" :max-value="80000000" v-bind:value="+entityCount" />
        </template>
        <input type="text" v-model="entityCount" />
      </LayoutSection>
    </div>
</template>

<script lang="ts">
    import Vue from 'vue';
    import LayoutSection from '~/components/LayoutSection.vue';
    import MultiLevelPieChart from "~/components/MultiLevelPieChart.vue";

    export default Vue.extend({
        components: {
            LayoutSection,
            MultiLevelPieChart
        },

        data() {
            return {
                entityCount: 20999542
            }
        },

        meta: {
            title: "New Dump"
        }
    })
</script>

<style scoped lang="scss">
  @import "~assets/base.scss";

  .chart-entity-count {
    height: 10rem;
  }

  .entity-selection {
    min-height: 10rem;
  }
</style>