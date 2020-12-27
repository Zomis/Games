<template>
  <div>
    <v-row
      v-for="(amount, resource) in caravan"
      :key="resource"
    >
      <v-col>
        <span class="resource"
          :class="{
            ['resource-' + resource]: true}"
          @click="takeMoney(resource)"
        >{{ amount }}</span>
      </v-col>
    </v-row>
  </div>
</template>
<script>
export default {
  name: "SpiceRoadResources",
  props: ["caravan"]
}
</script>
<style>
:root {
    --spiceRoad-yellow: #ffd166;
    --spiceRoad-red: #ef476f;
    --spiceRoad-green: #06D6A0;
    --spiceRoad-brown: #a5701e;
    --spiceRoad-silver: #b2b9c7;
    --spiceRoad-gold: #eea12c;
}

.resource {
    padding: 9px 14px;
    border-style: solid;
    border-width: thin;
    border-color: black !important;
    border-radius: 100%;
}
.resource-YELLOW {
    background-color: var(--spiceRoad-yellow) !important;
}
.resource-BROWN {
    background-color: var(--spiceRoad-brown) !important;
}
</style>