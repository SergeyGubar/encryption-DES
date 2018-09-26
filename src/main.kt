import java.lang.StringBuilder

private const val EXTENDER = "%"
private const val BLOCK_SIZE = 64
private const val SIZE_OF_CHAR = 8
private const val BLOCK_LENGTH = 8
private const val ROUNDS_QUANTITY = 16


private const val TEST_MESSAGE = "test asdf"
//private const val TEST_MESSAGE = "zxc%axasdf"
private const val TEST_KEY = "aasdaba"

fun main(args: Array<String>) {
    runEncodeDes(TEST_MESSAGE, TEST_KEY)
}

// TODO : Extend key if size != needed

fun runEncodeDes(message: String, key: String) {
    println("encoding message $message")
    val extendedMessage = extendMessage(message)
    val blocks = extractBinaryBlocks(extendedMessage).map { PermutationManager.performInitialPermutation(it) }
    val initialKey = extendKeyUnevenBits(key.toBinaryRepresentation())
    val result = encodeDes(blocks, initialKey)
    println("Encoded $result")
    runDecodeDes(result, key)
}

fun encodeDes(blocks: List<String>, initialKey: String): String {

    // Initial key is 64 bit (extended key already)

    val resultBlocks = Array(blocks.size) { it.toString() }

    // C and D, first of all C 0 and D 0
    var (keyLeft, keyRight) = PermutationManager.getInitialKeyPermutationValues()

    var key: String

    for (i in 0 until ROUNDS_QUANTITY) {

        keyLeft = PermutationManager.shiftKeyPartsLeft(keyLeft, i)
        keyRight = PermutationManager.shiftKeyPartsLeft(keyRight, i)

        // C + D
        val resultPermutation = mutableListOf<Int>()
        resultPermutation.addAll(keyLeft)
        resultPermutation.addAll(keyRight)

        // 56 bit
        val newKey = resultPermutation
                .map { initialKey[it - 1].toString() }
                .reduce { a, b -> a.plus(b) }

        // Get 48 bit key
        key = PermutationManager.performEncodeKeyPermutation(newKey)

        blocks.forEachIndexed { index, block ->
            val (l, r) = splitInTwo(block)
            val (left, right) = encodeRound(l, r, key)
            resultBlocks[index] = left.plus(right)
        }
    }

    return resultBlocks
            .map { PermutationManager.performReverseInitialPermutation(it) }
            .reduce { a, b -> a.plus(b) }
}

// 64 bit blocks, without whitespace
fun runDecodeDes(input: String, key: String) {
    val blocks = splitInBlocks(input, 64).map { PermutationManager.performInitialPermutation(it) }
    val initialKey = extendKeyUnevenBits(key.toBinaryRepresentation())
    val result = decodeDes(blocks, initialKey)
    println("Decoded ${result.fromBinaryRepresentation()}")
}

fun decodeDes(blocks: List<String>, initialKey: String): String {
    val resultBlocks = Array(blocks.size) { it.toString() }

    // C and D, first of all C 0 and D 0
    var (keyLeft, keyRight) = PermutationManager.getInitialKeyPermutationValues()

    var key: String

    for (i in 0 until ROUNDS_QUANTITY) {

        keyLeft = PermutationManager.shiftKeyPartsRight(keyLeft, i)
        keyRight = PermutationManager.shiftKeyPartsRight(keyRight, i)

        // C + D
        val resultPermutation = mutableListOf<Int>()
        resultPermutation.addAll(keyLeft)
        resultPermutation.addAll(keyRight)

        // 56 bit
        val newKey = resultPermutation
                .map { initialKey[it - 1].toString() }
                .reduce { a, b -> a.plus(b) }

        // Get 48 bit key
        key = PermutationManager.performEncodeKeyPermutation(newKey)

        blocks.forEachIndexed { index, block ->
            val (l, r) = splitInTwo(block)
            val (left, right) = decodeRound(l, r, key)
            resultBlocks[index] = left.plus(right)
        }
    }
    return resultBlocks
            .map { PermutationManager.performReverseInitialPermutation(it) }
            .reduce { a, b -> a.plus(b) }
}

fun encodeRound(left: String, right: String, key: String): Pair<String, String> {
    require(right.length == 32 && left.length == 32 && key.length == 48)
    val newRight = xor(left, f(right, key))
    return right to newRight
}

fun decodeRound(left: String, right: String, key: String): Pair<String, String> {
    require(right.length == 32 && left.length == 32 && key.length == 48)
    val newLeft = xor(right, f(left, key))
    return newLeft to left
}

fun f(right: String, key: String): String {
    val expandedRight = e(right)
    require(expandedRight.length == 48) { "Expanded key must be 48 bits! " }
    var result = xor(expandedRight, key)
    // 6 bits blocks
    val bBlocks = splitInBlocks(result, 6)
    // 4 bits blocs
    result = s(bBlocks).reduce { a, b -> a.plus(b) }

    result = PermutationManager.performPPermutation(result)
    return result
}

fun e(right: String): String {
    require(right.length == 32)
    return PermutationManager.performExpandPermutation(right).also { require(it.length == 48) }
}

fun s(input: List<String>): List<String> {
    require(input.all { it.length == 6 }) { "All elements must be 6 bitted!" }
    val result = mutableListOf<String>()
    input.forEachIndexed { index, block ->
        var fourBitBlock = block

        val sb = StringBuilder()
        sb.append(fourBitBlock.first())
        sb.append(fourBitBlock.last())

        val a = Integer.parseInt(sb.toString(), 2)
        fourBitBlock = fourBitBlock.drop(1)
        fourBitBlock = fourBitBlock.dropLast(1)
        val b = Integer.parseInt(fourBitBlock, 2)

        val tableValue = PermutationManager.extractSValue(index, a, b)

        val binary = Integer.toBinaryString(tableValue)

        result.add(expandLeftZero(binary, 4))

    }
    require(result.all { it.length == 4 }) { "All result elements must be 4 bitted!" }
    return result
}

fun xor(left: String, right: String): String {
    require(left.length == right.length)
    val result = CharArray(left.length)
    left.forEachIndexed { index, char ->
        result[index] = if (char == right[index]) '0' else '1'
    }
    return String(result)
}

fun splitInBlocks(input: String, blockSize: Int): List<String> {
    require(input.length % blockSize == 0) {
        "Input with length ${input.length} cannot be splitted" +
                " to blocks with $blockSize!"
    }
    val result = mutableListOf<String>()
    (0 until input.length step blockSize).forEach { index ->
        result.add(input.substring(index, index + blockSize))
    }

    return result
}

fun extendMessage(message: String): String {
    val newMessage = StringBuilder(message)
    while ((newMessage.length * SIZE_OF_CHAR) % BLOCK_SIZE != 0) {
        newMessage.append(EXTENDER)
    }
    return newMessage.toString()
}

fun extendKeyUnevenBits(key: String): String {
    require(key.length == 56) { "Size must be 56 bits to expand to 64!" }
    val result = mutableListOf<String>()
    (0 until key.length step 7)
            .map {
                val keyBlock = key.substring(it, it + 7)
                val chars = keyBlock.toCharArray().toMutableList()
                var numberOfOnes = 0
                chars.forEach {
                    if (it == '1') {
                        numberOfOnes++
                    }
                }
                val appendChar = if (numberOfOnes % 2 == 0) '1' else '0'
                val sb = StringBuilder(appendChar.toString())
                chars.forEach { sb.append(it) }
                result.add(sb.toString())
            }

    require(result.size == 8 && result.all { it.length == 8 })
    return result.reduce { a, b -> a.plus(b) }
}

fun extractBinaryBlocks(input: String): Array<String> =
        (0 until input.length step BLOCK_LENGTH)
                .map { input.substring(it, it + BLOCK_LENGTH).toBinaryRepresentation() }
                .toTypedArray()

fun splitInTwo(input: String, position: Int = BLOCK_SIZE / 2): Pair<String, String> {
    val left = input.substring(0, position)
    val right = input.substring(position, input.length)
    require(left.length == BLOCK_SIZE / 2 && right.length == BLOCK_SIZE / 2)
    return left to right
}

fun expandLeftZero(input: String, expectedLength: Int = SIZE_OF_CHAR): String {
    val sb = StringBuilder(input)
    while (sb.length < expectedLength) {
        sb.insert(0, "0")
    }
    return sb.toString()
}

fun String.toBinaryRepresentation(): String {
    val sb = StringBuilder()
    this.toCharArray().forEach {
        val append = expandLeftZero(Integer.toBinaryString(it.toInt()))
        sb.append(append)
    }
    return sb.toString()
}

fun String.fromBinaryRepresentation(): String {
    val result = StringBuilder()
    var counter = 0
    (0 until this.length step SIZE_OF_CHAR).forEachIndexed { index, element ->
        val binary = Integer.parseInt(this.substring(counter, counter + SIZE_OF_CHAR), 2)
        val char = binary.toChar()
        result.append(char)
        counter += SIZE_OF_CHAR
    }
    return result.toString()
}