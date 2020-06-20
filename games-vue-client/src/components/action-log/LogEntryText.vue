<template>
    <v-tooltip bottom :disabled="!useTooltip">
        <template v-slot:activator="{ on }">
            <span v-on="on" :class="cssClasses">{{ text }}</span>
        </template>
        <span>
            <component v-if="useTooltip" :is="tooltipComponent" v-bind="hoverBindings" />
        </span>
    </v-tooltip>
</template>
<script>
export default {
    name: "LogEntryText",
    props: ["text", "onHighlight", "tooltipComponent", "hoverBindings"],
    computed: {
        cssClasses() {
            return {
                ['has-tooltip']: this.useTooltip
            }
        },
        useTooltip() {
            return typeof this.tooltipComponent !== 'undefined'
        }
    }
}
</script>
<style scoped>
.has-tooltip {
    text-decoration: underline;
}
</style>