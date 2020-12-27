<template>
  <v-card>
    <v-card-title />
    <v-card-text>
      <div v-if="card.trade">
        <v-icon
          color="green"
        >
          mdi-cube
        </v-icon>
      </div>
    </v-card-text>
  </v-card>
</template>
<script>
export default {
    name: "SpiceRoadCard",
    props: ["card", "actions", "context"]
}
</script>
<style>
:root{
    --spiceRoad-yellow: #ffd166;
    --spiceRoad-red: #ef476f;
    --spiceRoad-green: #06D6A0;
    --spiceRoad-brown: #a5701e;
    --spiceRoad-silver: #b2b9c7;
    --spiceRoad-gold: #eea12c;
}

.coin-gold,
.coin-silver {
    padding: 9px 14px;
    border-style: solid;
    border-width: thin;
    border-color: black !important;
    border-radius: 100%;
}
.coin-gold {
    background-color: var(--spiceRoad-gold) !important;
}
.coin-silver {
    background-color: var(--spiceRoad-silver) !important;
}
</style>