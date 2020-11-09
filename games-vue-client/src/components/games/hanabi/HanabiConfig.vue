<template>
  <v-card>
    <!--
        <v-switch v-model="config.viewAllowCardIsNot" label="Allow See Card Is Not" disabled />
        <v-switch v-model="config.viewAllowCardProbability" label="Allow See Card Probabilities" disabled />
        <v-switch v-model="config.rainbowWildcard" label="Rainbow color is all colors instead of a sixth color" disabled />
        <v-switch v-model="config.namePlayingCard" label="Must name playing card" disabled />
        -->

    <v-switch
      v-model="config.rainbowExtraColor"
      label="Use Rainbow color"
    />
    <v-switch
      v-model="config.rainbowOnlyOne"
      label="Only one card each of rainbow color"
    />

    <v-switch
      v-model="config.allowEmptyClues"
      label="Allow Empty Clues"
    />
    <v-switch
      v-model="config.playUntilFullEnd"
      label="Play until max fails or all cards played"
      disabled
    />

    <v-text-field
      v-model="config.maxClueTokens"
      type="number"
      label="Max clue tokens"
    />
    <v-text-field
      v-model="config.maxFailTokens"
      type="number"
      label="Max fail tokens"
    />
  </v-card>
</template>
<script>
//"maxClueTokens":8,"maxFailTokens":3,
export default {
    name: "HanabiConfig",
    props: ["config"]
}
</script>