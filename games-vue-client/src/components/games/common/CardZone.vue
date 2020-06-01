<template>
    <transition-group name="list-complete" tag="div"
        :class="['card-zone', 'animation-' + animation]">
        <slot :class="animation + '-item'" />
    </transition-group>
</template>
<script>
export default {
    name: "CardZone",
    props: {
        animation: { type: String, default: "list-complete" }
    }
}
</script>
