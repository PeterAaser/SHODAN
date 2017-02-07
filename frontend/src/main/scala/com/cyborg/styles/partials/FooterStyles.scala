package com.cyborg.styles.partials

import com.cyborg.styles.constants.StyleConstants
import com.cyborg.styles.fonts.{FontWeight, UdashFonts}
import com.cyborg.styles.utils.{MediaQueries, StyleUtils}

import scala.language.postfixOps
import scalacss.Defaults._

object FooterStyles extends StyleSheet.Inline {
  import dsl._

  val footer = style(
    backgroundColor.black,
    height(StyleConstants.Sizes.FooterHeight px),
    fontSize(1.2 rem),
    color.white,

    MediaQueries.phone(
      style(
        height.auto,
        padding(2 rem, `0`)
      )
    )
  )

  val footerInner = style(
    StyleUtils.relativeMiddle,

    MediaQueries.phone(
      style(
        top.auto,
        transform := "none"
      )
    )
  )

  val footerLogo = style(
    display.inlineBlock,
    verticalAlign.middle,
    width(50 px),
    marginRight(25 px)
  )

  val footerLinks = style(
    display.inlineBlock,
    verticalAlign.middle
  )

  val footerMore = style(
    UdashFonts.acumin(FontWeight.SemiBold),
    marginBottom(1.5 rem),
    fontSize(2.2 rem)
  )

  val footerCopyrights = style(
    position.absolute,
    right(`0`),
    bottom(`0`),
    fontSize.inherit,

    MediaQueries.tabletPortrait(
      style(
        position.relative,
        textAlign.right
      )
    )
  )

  val footerAvsystemLink = style(
    StyleUtils.transition(),
    color.inherit,
    textDecoration := "underline",

    &.hover (
      color(StyleConstants.Colors.Yellow)
    ),

    &.visited (
      color.inherit,

      &.hover (
        color(StyleConstants.Colors.Yellow)
      )
    )
  )
}