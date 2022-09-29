package com.example.demomlv1

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@SpringBootApplication
class DemoMLv1Application

fun main(args: Array<String>) {
    runApplication<DemoMLv1Application>(*args)
}

@RestController
class RestController {

    var positionSucces = false
    var satelliteSplit = arrayOfNulls<Satellite>(3)

    // input: distancia al emisor tal cual se recibe en cada satélite
    // output: las coordenadas ‘x’ e ‘y’ del emisor del mensaje
    // func GetLocation(distances ...float32) (x, y float32)
    fun getLocation(distances: FloatArray): Position {

        //Divido en 100 los valores para simplificar las operaciones
        val d1 = distances[0] / 100
        val d2 = distances[1] / 100
        val d3 = distances[2] / 100

        //Se busca la intersección de las tres circunferencias con centro en la ubicación de los satélites
        //y radio las distancias al emisor
        //Se obtienen los puntos de intersección de 2 circunferencias
        //y luego se verifica en la ecuación de la tercer circunferencia estos puntos

        //Hallo los puntos de intersección de las circunferencias 1 y 2
        val b = 6 * d1.toDouble().pow(2.0) - 6 * d2.toDouble().pow(2.0) - 148
        val c = b.pow(2.0) - 4 * 37 * (((d1.toDouble().pow(2.0) - d2.toDouble()
            .pow(2.0) - 27).pow(2.0) / 4) - 25 + d1.toDouble()
            .pow(2.0) - 2 * d2.toDouble().pow(2.0))
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        positionSucces = false
        if (c == 0.0) { //una sóla solución
            x1 = b / 74
            y1 = (d1.toDouble().pow(2.0) - d2.toDouble().pow(2.0) - 12 * x1 - 27) / 2
        } else if (c > 0) { //2 soluciones
            x1 = (b + sqrt(c)) / 74
            x2 = (b - sqrt(c)) / 74
            y1 = (d1.toDouble().pow(2.0) - d2.toDouble().pow(2.0) - 12 * x1 - 27) / 2
            y2 = (d1.toDouble().pow(2.0) - d2.toDouble().pow(2.0) - 12 * x2 - 27) / 2
        }

        var x = 0.0
        var y = 0.0
        if (c >= 0) { //si c < 0 no hay soluciones
            //Verifico los puntos en la circunferencia 3, si verifica es la intersección
            val dist1 = sqrt((x1 - 5).pow(2.0) + (y1 - 1).pow(2.0))
            val dist2 = sqrt((x2 - 5).pow(2.0) + (y2 - 1).pow(2.0))
            if (abs(dist1 - d3) < 0.2) { // dejo un margen de aproximación
                x = x1
                y = y1
                positionSucces = true
            } else if (abs(dist2 - d3) < 0.2) {
                x = x2
                y = y2
                positionSucces = true
            }
        }
        return Position(x.toFloat() * 100, y.toFloat() * 100)
    }

    // input: el mensaje tal cual es recibido en cada satélite
    // output: el mensaje tal cual lo genera el emisor del mensaje
    // func GetMessage(messages ...[]string) (msg string)
    fun getMessage(messages:Array<Array<String>>): String {

        var message = ""

        val size1 = messages[0].size
        val size2 = messages[1].size
        val size3 = messages[2].size

        //Determino desfasaje
        val size: Int = if (size1 <= size2 && size1 <= size3)
            size1
        else if (size2 <= size1 && size2 <= size3)
            size2
        else size3

        val message1: Array<String> = if (size1 > size) {
            val d = size1 - size
            messages[0].drop(d).toTypedArray()
        } else messages[0]
        val message2: Array<String> = if (size2 > size) {
            val d = size2 - size
            messages[1].drop(d).toTypedArray()
        } else messages[1]
        val message3: Array<String> = if (size3 > size) {
            val d = size3 - size
            messages[2].drop(d).toTypedArray()
        } else messages[2]

        for (i in 0 until size) {
            if (message1[i] != "")
                message += message1[i]
            else if (message2[i] != "")
                message += message2[i]
            else if (message3[i] != "")
                message += message3[i]
            if (i < size - 1)
                message += " "
        }

        return message
    }

    @PostMapping("/topsecret")
    fun topsecret(@RequestBody satellites: Satellites): ResponseEntity<Respuesta> {

        //los envio en el orden necesario para que funcionen las ecuaciones de intersección
        val kenobi = satellites.satellites.find { it.name == "kenobi" }
        val skywalker = satellites.satellites.find { it.name == "skywalker" }
        val sato = satellites.satellites.find { it.name == "sato" }


        val kenobiD = kenobi!!.distance
        val skywalkerD = skywalker!!.distance
        val satoD = sato!!.distance

        val distances = FloatArray(3)
        distances[0] = kenobiD
        distances[1] = skywalkerD
        distances[2] = satoD

        val position = getLocation(distances)

        val kenobiM = kenobi.message
        val skywalkerM = skywalker.message
        val satoM = sato.message

        val messages = arrayOf(
            kenobiM,
            skywalkerM,
            satoM
        )

        val message = getMessage(messages)

        val respuesta = Respuesta(position, message)
        return if (message.trim() == "" || !positionSucces)
            ResponseEntity<Respuesta>(HttpStatus.NOT_FOUND)
        else
            ResponseEntity<Respuesta>(respuesta,  HttpStatus.OK)
    }

    @PostMapping(value=["/topsecret_split/{satellite_name}"])
    fun topsecretSplit(@PathVariable satellite_name:String, @RequestBody satellite: SatelliteSplit): ResponseEntity<String> {

        val distance = satellite.distance
        val message = satellite.message

        return if (message.isEmpty() || distance.isNaN())
            ResponseEntity<String>("Información incorrecta.", HttpStatus.NOT_FOUND)
        else {

            val satPost = Satellite(satellite_name, distance, message)
            when (satellite_name) {
                "kenobi" -> satelliteSplit[0] = satPost
                "skywalker" -> satelliteSplit[1] = satPost
                "sato" -> satelliteSplit[2] = satPost
            }
            ResponseEntity<String>("Información recibida de forma correcta.", HttpStatus.OK)
        }

    }

    @GetMapping(value=["/topsecret_split"])
    fun getTopsecretSplit(): ResponseEntity<Respuesta> {

        //los envio en el orden necesario para que funcionen las ecuaciones de intersección

        if (satelliteSplit[0] == null || satelliteSplit[1] == null || satelliteSplit[2] == null) {
            return ResponseEntity<Respuesta>(Respuesta(Position(0F, 0F), "No hay suficiente información"), HttpStatus.NOT_FOUND)
        }
        else {
            val distances = FloatArray(3)
            distances[0] = satelliteSplit[0]!!.distance
            distances[1] = satelliteSplit[1]!!.distance
            distances[2] = satelliteSplit[2]!!.distance

            val position = getLocation(distances)

            val messages = arrayOf(
                satelliteSplit[0]!!.message,
                satelliteSplit[1]!!.message,
                satelliteSplit[2]!!.message
            )

            val message = getMessage(messages)

            return if (message.trim() == "" || !positionSucces)
                ResponseEntity<Respuesta>(HttpStatus.NOT_FOUND)
            else
                ResponseEntity<Respuesta>(Respuesta(position, message),  HttpStatus.OK)
        }

    }

}

data class Position(val x: Float,  val y: Float)

data class Satellite(var name: String, var distance: Float, var message: Array<String>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Satellite

        if (name != other.name) return false
        if (distance != other.distance) return false
        if (!message.contentEquals(other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + distance.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }
}

data class Satellites(var satellites: ArrayList<Satellite>)

data class SatelliteSplit(val distance: Float, val message: Array<String>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SatelliteSplit

        if (distance != other.distance) return false
        if (!message.contentEquals(other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = distance.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }
}

data class Respuesta(val position: Position, var message: String)