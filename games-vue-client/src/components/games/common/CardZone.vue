<template>
    <transition-group name="list-complete" tag="div" :duration="20000"
        :class="['card-zone', 'animation-' + animation]">
        <slot />
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
<style>
/*.card-zone {
  position: relative;
}*/
/*.card-zone > .animate {
  transition: all 20s linear !important;
  display: inline-block !important;
  margin-right: 10px;
}*/
/*.list-complete-move {
  background-color: black;
}*/
/*.animate {
  transition: all 20s linear !important;
  display: inline-block !important;
  margin-right: 10px;
}
.list-complete-enter {
  opacity: 0;
  transform: translateY(30px);
}
.list-complete-leave-to {
  opacity: 0;
  transform: translateY(-30px);
}
.list-complete-leave-active {
  position: absolute !important;
}*/
</style>
