package com.sandinh.paho.akka

//++++ FSM states ++++//
sealed trait S
case object SDisconnected extends S
case object SConnected extends S
