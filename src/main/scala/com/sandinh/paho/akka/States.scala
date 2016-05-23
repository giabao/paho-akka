package com.sandinh.paho.akka

//++++ FSM states ++++//
sealed trait PSState
case object DisconnectedState extends PSState
case object ConnectedState extends PSState
