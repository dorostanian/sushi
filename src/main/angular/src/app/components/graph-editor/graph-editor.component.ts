import {Component, ElementRef, OnInit} from '@angular/core';
import {ActionBlock, Block, StateMachine} from "../../models/StateMachine";
import {GraphEdge, graphlib, layout} from "dagre";
import Graph = graphlib.Graph;
import * as d3 from 'd3';
import { range } from 'lodash';
import {ContainerElement} from "d3";

@Component({
  selector: 'app-graph-editor',
  templateUrl: './graph-editor.component.html',
  styleUrls: ['./graph-editor.component.scss']
})
export class GraphEditorComponent implements OnInit {
  private stateMachine: StateMachine = {
    blocks: [
      new ActionBlock('1', 'Block 1', ['2']),
      new ActionBlock('2', 'Block 2', ['3', '4']),
      new ActionBlock('3', 'Block 3', []),
      new ActionBlock('4', 'Block 4', ['5']),
      new ActionBlock('5', 'Block 5', []),
    ]
  };

  private blockRenderData: { [blockId: string]: BlockRenderData } = {};
  private svg: d3.Selection<SVGElement, any, any, any>;
  private frame: d3.Selection<SVGGElement, any, any, any>;

  private edgeLineFn = d3.line<Point>()
    .x((d: any) => d.x + 50)
    .y((d: any) => d.y + 50)
    .curve(d3.curveMonotoneY);

  private dragBlock = d3.drag()
    .on("start", () => this.blockDragStart())
    .on("drag", () => this.blockDrag());
    //.on("dragend", () => this.blockDragEnd());

  private zoom = d3.zoom()
    .scaleExtent([.5, 3])
    .on("zoom", () => this.zoomed());

  private blockDragStartPoint: Point;

  constructor(private elementRef: ElementRef) {}

  ngOnInit(): void {
    const svg = (this.elementRef.nativeElement as HTMLDivElement).getElementsByTagName('svg').item(0);
    this.svg = d3.select(svg);
    this.svg.call(this.zoom);
    this.frame = this.svg.append('g');
    this.render(this.stateMachine);
  }

  private render(stateMachine: StateMachine) {
    this.updateBlockRenderData(stateMachine, {pointsPerSide: 3, padding: 20, radius: 4});

    const blockRenderData = this.blockRenderData;

    const graph: Graph = new graphlib.Graph();
    graph.setGraph({});
    graph.setDefaultEdgeLabel(function() { return {}; });
    stateMachine.blocks.forEach(block => {
      graph.setNode(block.id, blockRenderData[block.id]);
    });
    stateMachine.blocks.forEach(block => {
      (block as ActionBlock).nextBlocks.forEach(nextBlock => {
        graph.setEdge(block.id, nextBlock);
      });
    });

    layout(graph);

    const edges = graph.edges().map(e => graph.edge(e));
    this.frame.selectAll('path.edge')
      .data(edges)
      .enter()
      .append('path')
      .attr('class', 'edge')
      .attr('d', (edge: GraphEdge) => this.edgeLineFn(edge.points))
      .attr('marker-end', 'url(#edge-arrow)');

    const blocks = this.frame.selectAll('g.block')
      .data(stateMachine.blocks);

    const enteringBlocks = blocks.enter()
      .append('g')
      .attr('class', 'block');

    enteringBlocks
      .append('rect')
      .attr('class', 'block')
      .attr('width', (b: Block) => blockRenderData[b.id].width)
      .attr('height', (b: Block) => blockRenderData[b.id].height)
      .call(this.dragBlock);

    enteringBlocks
      .each(function(b: Block) {
        d3.select(this)
          .selectAll('circle.connection-point')
          .data(blockRenderData[b.id].connectionPoints)
          .enter()
          .append('circle')
          .attr('class', 'connection-point')
          .attr('cx', (p: ConnectionPoint) => p.x)
          .attr('cy', (p: ConnectionPoint) => p.y)
          .attr('r', (p: ConnectionPoint) => p.r)
      });

    enteringBlocks
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('alignment-baseline', 'middle')
      .attr('dx', (b: Block) => blockRenderData[b.id].width / 2)
      .attr('dy', (b: Block) => blockRenderData[b.id].height / 2)
      .text((b: Block) => b.name)
      .call(this.dragBlock);

    this.updateBlockPositions();
  }

  private updateBlockPositions() {
    this.frame.selectAll('g.block')
      .attr('transform', (b: Block) => `translate(${this.blockRenderData[b.id].x},${this.blockRenderData[b.id].y})`);
  }

  private zoomed() {
    this.frame.attr('transform',
      `translate(${d3.event.transform.x},${d3.event.transform.y})scale(${d3.event.transform.k})`);
  }

  private blockDragStart() {
    const block: Block = d3.event.subject;
    const renderData: BlockRenderData = this.blockRenderData[block.id];
    const mouse = d3.mouse(this.svg.node() as ContainerElement);
    this.blockDragStartPoint = {x: mouse[0] - renderData.x, y: mouse[1] - renderData.y};
  }

  private blockDrag() {
    // console.log('>>> d3.event: ', d3.event);
    console.log('>>> d3.event.x: ', d3.event.x);
    const mouse = d3.mouse(this.svg.node() as ContainerElement);
    const block: Block = d3.event.subject;
    this.blockRenderData[block.id].x = mouse[0] - this.blockDragStartPoint.x;
    this.blockRenderData[block.id].y = mouse[1] - this.blockDragStartPoint.y;
    this.updateBlockPositions();
  }

  private updateBlockRenderData(stateMachine: StateMachine, connectionPointConfig: ConnectionPointConfig) {
    stateMachine.blocks.forEach(block => {
      if (!(block.id in this.blockRenderData)) {
        const width = 100;
        const height = 100;
        const connectionPoints: ConnectionPoint[] = [];
        range(connectionPointConfig.pointsPerSide).forEach(i => {
          connectionPoints.push({
            x: this.pointPosition(connectionPointConfig, height, i),
            y: 0,
            r: connectionPointConfig.radius
          });
          connectionPoints.push({
            x: this.pointPosition(connectionPointConfig, height, i),
            y: width,
            r: connectionPointConfig.radius
          });
          connectionPoints.push({
            x: 0,
            y: this.pointPosition(connectionPointConfig, width, i),
            r: connectionPointConfig.radius
          });
          connectionPoints.push({
            x: height,
            y: this.pointPosition(connectionPointConfig, width, i),
            r: connectionPointConfig.radius
          });
        });
        this.blockRenderData[block.id] = { width, height, x: 0, y: 0, connectionPoints };
      }
    });
  }

  private pointPosition(config: ConnectionPointConfig, totalSize: number, pointNr: number) {
    if (config.pointsPerSide === 1) {
      return totalSize / 2;
    }
    return config.padding + pointNr * (totalSize - 2 * config.padding) / (config.pointsPerSide - 1);
  }

  private drawEdge(b: Block) {

  }
}

interface ConnectionPointConfig {
  pointsPerSide: number;
  padding: number;
  radius: number;
}

interface BlockRenderData {
  width: number;
  height: number;
  x: number;
  y: number;
  connectionPoints: ConnectionPoint[];
}

interface Point {
  x: number;
  y: number;
}

interface ConnectionPoint extends Point {
  r: number;
}
