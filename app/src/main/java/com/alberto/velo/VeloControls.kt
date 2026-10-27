package com.alberto.velo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

internal val LocalVeloMotion = staticCompositionLocalOf { true }
internal fun Modifier.semanticsLabel(label: String) = semantics { contentDescription=label }
@Composable private fun duration() = if (LocalVeloMotion.current) 180 else 0

@Composable private fun ControlButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, style: Int,
    contentPadding: PaddingValues, content: @Composable RowScope.() -> Unit) {
    val c=MaterialTheme.colorScheme
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .97f else 1f,tween(duration()),label="Presión")
    val bg=when(style) { 0 -> c.primary; 1 -> c.primaryContainer; 2 -> c.surfaceVariant.copy(alpha=.35f); else -> Color.Transparent }
    val fg=if(style==0) c.onPrimary else if(style==1) c.onPrimaryContainer else c.primary
    Row(modifier.graphicsLayer { scaleX=scale; scaleY=scale; alpha=if(enabled) 1f else .38f }
        .heightIn(min=48.dp).clip(RoundedCornerShape(if(style==3) 14.dp else 18.dp))
        .background(bg).then(if(style==2) Modifier.border(1.dp,c.outline.copy(alpha=.65f),RoundedCornerShape(18.dp)) else Modifier)
        .clickable(interactionSource=interaction,indication=LocalIndication.current,enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(contentPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(TextStyle(fontSize=13.sp,fontWeight=FontWeight.SemiBold),content={ content() })
        }
    }
}
@Composable internal fun VButton(onClick: () -> Unit, modifier: Modifier=Modifier, enabled: Boolean=true, contentPadding: PaddingValues=PaddingValues(horizontal=18.dp,vertical=13.dp),content: @Composable RowScope.() -> Unit) = ControlButton(onClick,modifier,enabled,0,contentPadding,content)
@Composable internal fun VTonalButton(onClick: () -> Unit, modifier: Modifier=Modifier, enabled: Boolean=true, contentPadding: PaddingValues=PaddingValues(horizontal=18.dp,vertical=13.dp),content: @Composable RowScope.() -> Unit) = ControlButton(onClick,modifier,enabled,1,contentPadding,content)
@Composable internal fun VOutlinedButton(onClick: () -> Unit, modifier: Modifier=Modifier, enabled: Boolean=true, contentPadding: PaddingValues=PaddingValues(horizontal=16.dp,vertical=12.dp),content: @Composable RowScope.() -> Unit) = ControlButton(onClick,modifier,enabled,2,contentPadding,content)
@Composable internal fun VTextButton(onClick: () -> Unit, modifier: Modifier=Modifier, enabled: Boolean=true, contentPadding: PaddingValues=PaddingValues(horizontal=10.dp,vertical=10.dp),content: @Composable RowScope.() -> Unit) = ControlButton(onClick,modifier,enabled,3,contentPadding,content)
@Composable internal fun VIconButton(onClick: () -> Unit, modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable () -> Unit) {
    Box(modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)).clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary,content=content)
    }
}
@Composable internal fun VChip(selected: Boolean,onClick: () -> Unit,label: @Composable () -> Unit,modifier: Modifier=Modifier,leadingIcon: (@Composable () -> Unit)?=null,multiSelect: Boolean=false) {
    val c=MaterialTheme.colorScheme
    val bg by animateColorAsState(if(selected) c.primaryContainer else c.surfaceVariant.copy(alpha=.3f),tween(duration()),label="Selección")
    Row(modifier.heightIn(min=44.dp).clip(RoundedCornerShape(15.dp)).background(bg)
        .border(1.dp,if(selected) c.primary.copy(alpha=.5f) else c.outline.copy(alpha=.35f),RoundedCornerShape(15.dp))
        .then(if(multiSelect) Modifier.toggleable(value=selected,role=Role.Checkbox,onValueChange={onClick()}) else Modifier.selectable(selected=selected,role=Role.RadioButton,onClick=onClick)).padding(horizontal=13.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        CompositionLocalProvider(LocalContentColor provides if(selected) c.primary else c.onSurfaceVariant) {
            leadingIcon?.invoke(); ProvideTextStyle(TextStyle(fontSize=12.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal),label)
        }
    }
}
@Composable internal fun VSwitch(checked: Boolean,onCheckedChange: (Boolean) -> Unit,modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    val progress by animateFloatAsState(if(checked) 1f else 0f,tween(duration()),label="Interruptor")
    val bg by animateColorAsState(if(checked) c.primary else c.surfaceVariant,tween(duration()),label="Estado")
    Box(modifier.size(56.dp,48.dp).toggleable(value=checked,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(48.dp,30.dp)) {
            drawRoundRect(bg,cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))
            val x=15.dp.toPx()+progress*18.dp.toPx()
            drawCircle(if(checked)c.onPrimary else c.onSurfaceVariant,11.dp.toPx(),androidx.compose.ui.geometry.Offset(x,size.height/2))
            if(checked) {
                val path=Path().apply { moveTo(x-4.dp.toPx(),15.dp.toPx());lineTo(x-1.dp.toPx(),18.dp.toPx());lineTo(x+4.dp.toPx(),12.dp.toPx()) }
                drawPath(path,c.primary,style=androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
        }
    }
}
@Composable internal fun VField(value: String,onValueChange: (String) -> Unit,modifier: Modifier=Modifier,
    label: (@Composable () -> Unit)?=null,placeholder: (@Composable () -> Unit)?=null,
    leadingIcon: (@Composable () -> Unit)?=null,trailingIcon: (@Composable () -> Unit)?=null,
    singleLine: Boolean=false,keyboardOptions: KeyboardOptions=KeyboardOptions.Default,
    visualTransformation: VisualTransformation=VisualTransformation.None,shape: androidx.compose.ui.graphics.Shape=RoundedCornerShape(18.dp),labelText: String?=null) {
    val c=MaterialTheme.colorScheme
    val interaction=remember { MutableInteractionSource() }; val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if(focused)c.primary else c.outline.copy(alpha=.6f),tween(duration()),label="Campo activo")
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(labelText!=null) Text(labelText,fontSize=11.sp,fontWeight=FontWeight.Medium,color=c.onSurfaceVariant)
        else if(label!=null) CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant) { ProvideTextStyle(TextStyle(fontSize=11.sp,fontWeight=FontWeight.Medium),label) }
        BasicTextField(value,onValueChange,Modifier.fillMaxWidth().semantics { if(labelText!=null)contentDescription=labelText },singleLine=singleLine,keyboardOptions=keyboardOptions,
            visualTransformation=visualTransformation,interactionSource=interaction,cursorBrush=SolidColor(c.primary),
            textStyle=TextStyle(color=c.onSurface,fontSize=16.sp,lineHeight=23.sp),decorationBox={ inner ->
                Row(Modifier.clip(shape).background(c.surfaceVariant.copy(alpha=.38f)).border(if(focused)1.5.dp else 1.dp,border,shape)
                    .heightIn(min=58.dp).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(leadingIcon!=null) CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant,content=leadingIcon)
                    Box(Modifier.weight(1f)) { if(value.isEmpty()&&placeholder!=null) CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant) { ProvideTextStyle(TextStyle(fontSize=13.sp),placeholder) }; inner() }
                    trailingIcon?.invoke()
                }
            })
    }
}
@Composable internal fun VDialog(onDismissRequest: () -> Unit,title: @Composable () -> Unit,text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,dismissButton: (@Composable () -> Unit)?=null) {
    Dialog(onDismissRequest,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val c=MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().padding(horizontal=20.dp).imePadding().heightIn(max=680.dp),shape=RoundedCornerShape(30.dp),color=c.background,border=BorderStroke(1.dp,c.outline.copy(alpha=.6f))) {
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Box(Modifier.width(30.dp).height(3.dp).background(c.primary,RoundedCornerShape(2.dp)))
                ProvideTextStyle(TextStyle(fontSize=25.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),title)
                Box(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) { CompositionLocalProvider(LocalContentColor provides c.onSurface,content=text) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically) { dismissButton?.invoke(); confirmButton() }
            }
        }
    }
}
@Composable internal fun VSlider(value: Float,onValueChange: (Float) -> Unit,modifier: Modifier=Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,steps: Int=0,onValueChangeFinished: (() -> Unit)?=null) {
    val c=MaterialTheme.colorScheme; val currentChange by rememberUpdatedState(onValueChange); val finish by rememberUpdatedState(onValueChangeFinished)
    fun apply(x: Float,width: Float) { var fraction=(x/width).coerceIn(0f,1f); if(steps>0) fraction=(fraction*(steps+1)).roundToInt().toFloat()/(steps+1);currentChange(valueRange.start+fraction*(valueRange.endInclusive-valueRange.start)) }
    Canvas(modifier.fillMaxWidth().height(48.dp).semantics {
        progressBarRangeInfo=ProgressBarRangeInfo(value,valueRange,steps)
        setProgress { currentChange(it.coerceIn(valueRange.start,valueRange.endInclusive)); finish?.invoke();true }
    }.pointerInput(valueRange,steps) { detectTapGestures { apply(it.x,size.width.toFloat());finish?.invoke() } }
        .pointerInput(valueRange,steps) { detectDragGestures(onDragEnd={finish?.invoke()},onDragCancel={finish?.invoke()}) { change,_ -> change.consume(); apply(change.position.x,size.width.toFloat()) } }) {
        val fraction=((value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
        val y=size.height/2; val radius=5.dp.toPx(); val x=12.dp.toPx()+fraction*(size.width-24.dp.toPx())
        drawLine(c.surfaceVariant,androidx.compose.ui.geometry.Offset(12.dp.toPx(),y),androidx.compose.ui.geometry.Offset(size.width-12.dp.toPx(),y),10.dp.toPx(),StrokeCap.Round)
        drawLine(c.primary,androidx.compose.ui.geometry.Offset(12.dp.toPx(),y),androidx.compose.ui.geometry.Offset(x,y),10.dp.toPx(),StrokeCap.Round)
        drawRoundRect(c.primary,androidx.compose.ui.geometry.Offset(x-5.dp.toPx(),y-15.dp.toPx()),androidx.compose.ui.geometry.Size(10.dp.toPx(),30.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(radius))
        drawLine(c.onPrimary,androidx.compose.ui.geometry.Offset(x,y-5.dp.toPx()),androidx.compose.ui.geometry.Offset(x,y+5.dp.toPx()),1.5.dp.toPx(),StrokeCap.Round)
    }
}

@Composable internal fun VProgress(progress: Float?=null,modifier: Modifier=Modifier,color: Color=MaterialTheme.colorScheme.primary,trackColor: Color=color.copy(alpha=.12f)) {
    val motion=LocalVeloMotion.current
    val phase=if(progress==null&&motion) rememberInfiniteTransition(label="Carga").animateFloat(0f,1f,infiniteRepeatable(tween(1400,easing=LinearEasing)),label="Recorrido").value else 0f
    Canvas(modifier.fillMaxWidth().heightIn(min=4.dp).semantics { progressBarRangeInfo=if(progress==null)ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress,0f..1f) }) {
        val y=size.height/2
        drawLine(trackColor,androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),size.height,StrokeCap.Round)
        val start=if(progress==null)phase*.7f else 0f
        val end=progress?.coerceIn(0f,1f)?: (start+.3f)
        drawLine(color,androidx.compose.ui.geometry.Offset(size.width*start,y),androidx.compose.ui.geometry.Offset(size.width*end,y),size.height,StrokeCap.Round)
    }
}
@Composable internal fun VLoading(modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme;val motion=LocalVeloMotion.current
    val phase=if(motion)rememberInfiniteTransition(label="Esperando").animateFloat(0f,360f,infiniteRepeatable(tween(1600,easing=LinearEasing)),label="Órbita").value else 0f
    Canvas(modifier.size(28.dp).semantics { progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate }) {
        drawCircle(c.primary.copy(alpha=.15f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawArc(c.primary,phase,100f,false,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
