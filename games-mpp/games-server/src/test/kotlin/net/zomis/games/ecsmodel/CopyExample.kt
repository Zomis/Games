package net.zomis.games.ecsmodel

class CopyExample {

    var value = 5
    // TODO: Pass a context to this lambda which can resolve into a new value for the copy
    var test: () -> Boolean = { value == 4 }

    fun copy(): CopyExample {
        val copy = CopyExample()
        copy.value = this.value
        copy.test = this.test
        return copy
    }

}