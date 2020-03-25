<template>
  <ul class="dropdown">
    <li>
      <input type="radio" :name="radioName" :id="radioName + '-best'" value="best" :checked="value === 'best'">
      <label :for="radioName + '-best'">best in group</label>
    </li>

    <li>
      <input type="radio" :name="radioName" :id="radioName + '-non-deprecated'" value="non-deprecated" :checked="value === 'non-deprecated'">
      <label :for="radioName + '-non-deprecated'">not deprecated</label>
    </li>

    <li>
      <input type="radio" :name="radioName" :id="radioName + '-any'" value="any" :checked="value === 'any'">
      <label :for="radioName + '-any'">any rank</label>
    </li>
  </ul>
</template>

<script lang="ts">
    import Vue from 'vue';
    import {makeId} from '~/helpers/random.ts';

    export default Vue.extend({
        name: "StatementPatternRank",
        props: {
            value: {
                default: "non-deprecated",
            }
        },
        data() {
            return {
                radioName: makeId(),
            }
        },
    });
</script>

<style scoped lang="scss">
  @import '~assets/_base.scss';

  .dropdown {
    /* remove list styling */
    margin: 0;
    padding: 0 0 0 1rem;
    list-style-type: none;
    display: inline-block;
    vertical-align: bottom;

    /* remove radio button styling */
    input[type='radio'] {
      -webkit-appearance: none;
      appearance: none;
      width: 0;
      height: 0;
      border: 0;
      outline: 0;
    }

    li {
      display: flex; /* so that whitespace is not significant */
      margin: 0;
      padding: 0;
    }

    label {
      line-height: 2rem;
      height: 4rem;
      width: 6rem;
      background-color: $color-accent90;
    }

    input:checked + label {
      display: block;
      background-color: $color-accent30;
      color: white;
    }

    /* hide unchecked labels if not focused or hovered */
    label {
      display: none;

      &:hover {
        background-color: $color-accent50;
        color: white;
      }
    }

    &:hover label, &:focus-within label {
      display: block;
    }

    &:before {
      content : "▼";
      position: absolute;
      left: 0;
      line-height: 2rem;
      width: 1rem;
      color: white;
      height: 4rem;
      background-color: $color-accent30;
    }
    position: relative;
  }
</style>