<template>
    <v-tooltip v-model="tooltipActive" bottom :disabled="!useTooltip" :open-on-hover="false">
        <template v-slot:activator="{ on }">
            <span v-on="on" :class="cssClasses"
                    @click="clicked = !clicked"
                    @mouseover="hover = true"
                    @mouseleave="hover = false">{{ text }}</span>
        </template>
        <span>
            <component v-if="useTooltip" :is="tooltipComponent" v-bind="hoverBindings" />
        </span>
    </v-tooltip>
</template>
<script>
export default {
    name: "LogEntryText",
    props: ["text", "onHighlight", "tooltipComponent", "hoverBindings", "private"],
    data() {
        return {
            tooltipActive: false,
            hover: false,
            clicked: false
        }
    },
    watch: {
        hover(value) {
            this.tooltipActive = value || this.clicked
        },
        clicked(value) {
            this.tooltipActive = value || this.hover
        }
    },
    computed: {
        cssClasses() {
            return {
                ['has-tooltip']: this.useTooltip,
                ['private-log']: this.private
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
    cursor: pointer;
    text-decoration: underline;
}

.private-log {
    font-style: italic;
}
</style>
