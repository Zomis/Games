<template>
    <div>
        <v-tooltip bottom>
            <template v-slot:activator="{ on }">
                <div @click="click()" v-on="on" :class="{ actionable: actions.available[action] }">
                    {{ card.name }}
                </div>
            </template>
            <div v-if="card.health">
                {{ card.health }}
            </div>
            <div v-else>
                <div v-for="(symbol, index) in card.symbols" :key="index">{{ symbol }}</div>
            </div>
        </v-tooltip>
    </div>
</template>
<script>
export default {
    name: "DungeonMayhemCard",
    props: ["card", "actionType", "action", "actions"],
    methods: {
        click() {
            this.actions.perform(this.actionType, this.action)
        }
    }
}
</script>