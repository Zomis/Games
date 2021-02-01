<template>
  <v-tooltip bottom :open-on-hover="showTooltip">
    <template v-slot:activator="{ on, attrs }">
      <v-btn
        v-bind="attrs"
        @click="actions.perform('', item)"
        v-on="on"
      >
        {{ item }}
      </v-btn>
    </template>
    <span>
      hello
    </span>
  </v-tooltip>
</template>

<script>
    export default {
        name: "ActionButton",
        props: {
            actions: { type: Object, required: true },
            item: { type: String , required: true},
            descriptions: { type: Object, default: null, required: false},
        },
        data() {
            return {
                showTooltip: this.descriptions != null && this.descriptions[this.item] != null
            }
        }
    }
</script>