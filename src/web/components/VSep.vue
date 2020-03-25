<template>
  <span class="vsep">
    <slot></slot>
  </span>
</template>

<script>
    export default {
        name: "VSep"
    }
</script>

<style scoped>
  .vsep {
    position: relative;
    line-height: 10ex;
  }

  .vsep::before {
    content: "";
    display: block;
    position: absolute;
    width: 1px;
    height: 4ex;
    top: 100%;
    left: 50%;
    background-color: black;
  }
  .vsep::after {
    content: "";
    display: block;
    position: absolute;
    width: 1px;
    height: 4ex;
    bottom: 100%;
    left: 50%;
    background-color: black;
  }
</style>