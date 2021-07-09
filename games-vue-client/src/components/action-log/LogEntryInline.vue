<template>
  <component
    :is="component"
    :private="privateLog"
    :context="context"
    v-bind="bindings"
  />
</template>
<script>
export default {
  name: "LogEntryInline",
  props: ["private", "context", "component", "bindings"],
  computed: {
    privateLog() { return this.private } // "private" is reserved in template apparently
  }
}
</script>
