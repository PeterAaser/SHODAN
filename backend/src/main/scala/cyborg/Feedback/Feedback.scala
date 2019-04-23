package cyborg

package object feedback {

  case class ScoredPhenotype[A](score: Double, error: Double, phenotype: A){
    override def toString = s"Scored phenotype:\nScore: $score\nerror: $error\npheno name: $phenotype"
  }
}
