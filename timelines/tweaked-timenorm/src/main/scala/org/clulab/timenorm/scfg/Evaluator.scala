package org.clulab.timenorm.scfg

import scala.io.{Codec, Source}
import scala.util.{Failure, Success}
import java.io.File
import java.io.PrintWriter

object Evaluator {
  /**
  This program normalizes timexes and compares the results to their gold
  standard normalizations
  */

  def main(lang: String, inFile: String, outFile: String): Unit = {
    /**
    Enter the language ("es"/"en") and the input and output paths

    Both input and output files are .tsv files with timexes in the 1st column,
    gold normalization value in the 2nd column and, in the output file, system
    normalization value in the 3rd column. Timexes from different documents must
    be separated by newlines, being DCTs the first timexes from each document
    */

    // Obtain the data of timexes and gold values from the input file
    val (timexList, goldList) = getContent(inFile)
    // Obtain the normalizations of the timexes.
    val normList = getNormalizations(lang, timexList)
    // Compare gold and system normalizations, write the results and get the
    //sums of timexes and correct normalizations
    val (sumGold, sumNorm) = compareAndWrite(outFile, timexList, goldList, normList)

    // Compute number of errors and accuracy
    val sumErrors = sumGold - sumNorm
    val accuracy = sumNorm.toFloat * 100 / sumGold

    // Print the final statistics
    println(f"""\n
        |Number of timexes (also DCTs): $sumGold%6d
        |Correct normalizations:        $sumNorm%6d
        |Incorrect normalizations:      $sumErrors%6d
        |Accuracy:                      $accuracy%6.2f\n""".stripMargin)
  }

  def getContent(inFile: String): (List[String], List[String]) = {
    /** Obtains the content from the input file as timex and value lists */

    val source = Source.fromFile(inFile)(Codec.UTF8)
    val lines = try {
      source.getLines.toList
    }
    finally {
      source.close()
    }
    val content = lines.map { line =>
      val split = line.split('\t')
      (split.lift(0).getOrElse(""), split.lift(2).getOrElse(""))
    }.unzip
    println(content)
    content
  }

  def getNormalizations(lang: String, timexList: List[String]): List[String] = {
    /** Processes the data, sends timexes and DCTs to the normalizer and returns
    the list with all the normalizations */

    // Select the parser for the desired grammar depending on the language
    val parser = lang match {
      case "es" => TemporalExpressionParser.es()
      case "en" => TemporalExpressionParser.en()
      case "it" => TemporalExpressionParser.it()
    }

    var dctTimeSpanOpt: Option[TimeSpan] = None
    val normList = timexList.map { timex =>
      // If this is a timex (is not a doc separator):
      if (timex.nonEmpty) {
        println(timex)
        // If this is the first timex in a doc, consider it a DCT
        if (dctTimeSpanOpt.isEmpty)
          dctTimeSpanOpt = Some(mkTimeSpan(timex))
        // Normalize the timex and append the normalization
        normalize(parser, timex, dctTimeSpanOpt.get)
      }
      // If this is a doc separator, empty the DCT timex and append ""
      else {
        dctTimeSpanOpt = None
        ""
      }
    }

    normList
  }

  def mkTimeSpan(timex: String): TimeSpan = {
    val timeSpan = timex.replace('T', '-').replace(':', '-').split('-').map(_.toInt) match {
      case Array(year, month, day) => TimeSpan.of(year, month, day)
      case Array(year, month, day, hour, minute, second) => TimeSpan.of(year, month, day, hour, minute, second)
      case Array(year, month, day, hour, minute) => TimeSpan.of(year, month, day, hour, minute, 0)
    }

    timeSpan
  }

  def normalize(parser: TemporalExpressionParser, timex: String, dctTimeSpan: TimeSpan): String = {
    /** Normalizes a timex according to the parser and the DCT TimeSpan.
    DCTs are normalized with respect to themselves */

    // Parse the timex with respect to its anchor
    parser.parse(timex, dctTimeSpan) match {
      // If the parser fails, return an empty string as normalization
      case Failure(_) => "-"
      // If the parser successes, return the normalization of the timex
      case Success(temporal) => temporal.timeMLValue
    }
  }

  def compareAndWrite(outFile: String, timexList: List[String],
      goldList: List[String], normList: List[String]): (Int, Int) = {
    // Create the output writer
    val printWriter = new PrintWriter(new File(outFile), Codec.UTF8.toString)
    try {
      compareAndWrite(printWriter, timexList, goldList, normList)
    }
    finally {
      printWriter.close()
    }
  }

  def compareAndWrite(printWriter: PrintWriter, timexList: List[String],
      goldList: List[String], normList: List[String]): (Int, Int) = {
    /** Writes the results to the printWriter, in "{timex}\t{gold}\t{norm}"
    format, and counts the number of timexes and correct normalizations */

    def next(current: Int, condition: Boolean): Int = if (condition) current + 1 else current

    // Iterate over timex list and get each timex, gold and norm set
    val goldAndNormCounters = (timexList, goldList, normList).zipped.foldLeft(0, 0) { case ((goldCounter, normCounter), (timex, gold, norm)) =>
      println(s"$timex\t$gold\t$norm")
      val (isGold, isNorm) = if (timex.nonEmpty) {
        // If this is a timex, write the data
        printWriter.println(s"$timex\t$gold\t$norm")
        // If this is a timex, sum a gold value
        // If timex exists in corpus and normalization is equal to gold,
        // sum a correct norm value
        (true, gold != "-" && norm == gold)
      }
      else {
        // If this is a doc separator, write a newline
        printWriter.println()
        (false, false)
      }

      (next(goldCounter, isGold), next(normCounter, isNorm))
    }
    goldAndNormCounters
  }
}
