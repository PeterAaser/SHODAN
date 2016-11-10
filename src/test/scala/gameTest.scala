package SHODAN

import org.scalatest.FlatSpec
import wallAvoid._

class SetSpec extends FlatSpec {

  var gameState = new Agent( Coord(40.0, 50.0), PI/2.0, 90)

  "An agent with 90 degrees FoV" should s"have have a FoV of ${PI/2.0} radians" in {
    assert(
      gameState.radFieldOfView <= PI/2.0 + 0.01 &&
      gameState.radFieldOfView >= PI/2.0 - 0.01
    )
  }

  "An agent with with 4 viewPoints" should "have have 4 viewAngles" in {
    assert(
      gameState.viewAngles.size == 4
    )
  }

  /////////////////////////
  /////////////////////////
  /////////////////////////

  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"Have eye #1 looking in direction ${(PI/2.0) + (PI/4.0)}}" in {
    assert(
      gameState.viewAngles(3) <= PI/2.0 + PI/4.0 +0.01 &&
      gameState.viewAngles(3) >= PI/2.0 + PI/4.0 -0.01
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"Have eye #2 looking in direction ${PI/2 + PI/4 - PI/6}" in {
    assert(
      gameState.viewAngles(2) <= PI/2.0 + PI/4.0 - 1*PI/6 +0.01 &&
      gameState.viewAngles(2) >= PI/2.0 + PI/4.0 - 1*PI/6 -0.01
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"Have eye #3 looking in direction ${PI/2 + PI/4 - 2*PI/6.0}" in {
    assert(
      gameState.viewAngles(1) <= PI/2.0 + PI/4.0 - 2.0*PI/6  +0.01 &&
      gameState.viewAngles(1) >= PI/2.0 + PI/4.0 - 2.0*PI/6  -0.01
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"Have eye #4 looking in direction ${PI/2 + PI/4 - 3*PI/6.0}" in {
    assert(
      gameState.viewAngles(0) <= PI/2.0 + PI/4.0 - 3.0*PI/6  +0.01 &&
      gameState.viewAngles(0) >= PI/2.0 + PI/4.0 - 3.0*PI/6  -0.01
    )
  }

  /////////////////////////
  /////////////////////////
  /////////////////////////

  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"see a wall at distance 56.568 with eye #1 " in {
    assert(
      gameState.distances(3) <= 70.71 + 0.3 &&
      gameState.distances(3) >= 70.71 - 0.3
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"see a wall at distance 51.77 with eye #2 " in {
    assert(
      gameState.distances(2) <= 51.771 + 0.3 &&
      gameState.distances(2) >= 51.771 - 0.3
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"see a wall at distance 51.77 with eye #3 " in {
    assert(
      gameState.distances(1) <= 51.771 + 0.3 &&
      gameState.distances(1) >= 51.771 - 0.3
    )
  }
  s"An agent located at 50.0, 40.0 with heading ${PI/2} and a 90 degrees FoV" should
  s"see a wall at distance 64.031 with eye #4 " in {
    assert(
      gameState.distances(0) <= 56.568 + 0.3 &&
      gameState.distances(0) >= 56.568 - 0.3
    )
  }


  it should "produce NoSuchElementException when head is invoked" in {
    assertThrows[NoSuchElementException] {
      Set.empty.head
    }
  }


  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////
  /////////////////////////

  val input = List(1.0, -1.0)
  val (newGameState, distances) = Agent.updateAgent(gameState, input)

  println(newGameState)

  s"An agent located at 50.0, 40.0 with heading receiving input 1.0, -1.0" should
  s"have moved 10 units north" in {
    assert(
      newGameState.loc.x >= 40.0 - 0.01 &&
      newGameState.loc.x <= 40.0 + 0.01
    )
    assert(
      newGameState.loc.y >= 40.0 - 0.01 &&
      newGameState.loc.y <= 40.0 + 0.01
    )
  }

  s"An agent located at 50.0, 40.0 with heading receiving input 1.0, -1.0" should
  s"now be headed ~${2.0*0.01 + PI/2.0}" in {
    assert(
      newGameState.heading >= gameState.heading + 2.0*0.01 - 0.001 &&
      newGameState.heading <= gameState.heading + 2.0*0.01 + 0.001
    )
  }
}
