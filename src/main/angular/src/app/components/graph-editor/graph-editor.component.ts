import {Component, ElementRef, OnInit} from '@angular/core';
import {ActionBlock, Block, StateMachine} from "../../models/StateMachine";
import {Edge, GraphEdge, graphlib, layout} from "dagre";
import Graph = graphlib.Graph;
import * as d3 from 'd3';
import { range, minBy } from 'lodash';
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

  private graph: Graph = new graphlib.Graph();

  private blockRenderData: { [blockId: string]: BlockRenderData } = {};
  private svg: d3.Selection<SVGElement, any, any, any>;
  private frame: d3.Selection<SVGGElement, any, any, any>;

  private edgeLineFn = d3.line<Point>()
    .x((d: any) => d.x)
    .y((d: any) => d.y)
    .curve(d3.curveMonotoneY);

  private dragBlock = d3.drag()
    .on("start", () => this.blockDragStart())
    .on("drag", () => this.blockDrag());

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

    this.graph.setGraph({});
    this.graph.setDefaultEdgeLabel(function() { return {}; });

    this.render(this.stateMachine);
  }

  private render(stateMachine: StateMachine) {
    this.updateBlockRenderData(stateMachine, {pointsPerSide: 3, padding: 20, radius: 4});

    const blockRenderData = this.blockRenderData;

    stateMachine.blocks.forEach(block => {
      this.graph.setNode(block.id, blockRenderData[block.id]);
    });
    stateMachine.blocks.forEach(block => {
      (block as ActionBlock).nextBlocks.forEach(nextBlock => {
        this.graph.setEdge(block.id, nextBlock);
      });
    });

    layout(this.graph);

    this.updateEdges();

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

  private updateEdges() {
    const edges = this.graph.edges().map((edge: Edge) =>
      this.getEdge(this.blockRenderData[edge.v], this.blockRenderData[edge.w]));

    this.frame.selectAll('path.edge')
      .data(edges)
      .enter()
      .append('path')
      .attr('class', 'edge')
      .attr('marker-end', 'url(#edge-arrow)');

    this.frame.selectAll('path.edge')
      .attr('d', (points: Point[]) => this.edgeLineFn(points));
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
    const mouse = this.mouse();
    this.blockDragStartPoint = {x: mouse.x - renderData.x, y: mouse.y - renderData.y};
  }

  private blockDrag() {
    const mouse = this.mouse();
    const block: Block = d3.event.subject;
    this.blockRenderData[block.id].x = mouse.x - this.blockDragStartPoint.x;
    this.blockRenderData[block.id].y = mouse.y - this.blockDragStartPoint.y;
    this.updateBlockPositions();
    this.updateEdges();
  }

  private updateBlockRenderData(stateMachine: StateMachine, connectionPointConfig: ConnectionPointConfig) {
    stateMachine.blocks.forEach(block => {
      if (!(block.id in this.blockRenderData)) {
        const width = 100;
        const height = 100;
        const connectionPoints: ConnectionPoint[] = [];
        range(connectionPointConfig.pointsPerSide).forEach(i => {
          [{
            x: this.pointPosition(connectionPointConfig, height, i),
            y: 0,
          }, {
            x: this.pointPosition(connectionPointConfig, height, i),
            y: width,
          }, {
            x: 0,
            y: this.pointPosition(connectionPointConfig, width, i),
          }, {
            x: height,
            y: this.pointPosition(connectionPointConfig, width, i),
          }].forEach(p => {
            connectionPoints.push({
              x: p.x,
              y: p.y,
              r: connectionPointConfig.radius,
              priority: p.x === width / 2 || p.y === height / 2 // priority point if point in middle
            });
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

  private mouse(): Point {
    const mouse = d3.mouse(this.svg.node() as ContainerElement);
    return { x: mouse[0], y: mouse[1] };
  }

  private getEdge(from: BlockRenderData, to: BlockRenderData): Point[] {
    const absPointsFrom: ConnectionPoint[] = from.connectionPoints
      .map(p => ({x: from.x + p.x, y: from.y + p.y, r: p.r, priority: p.priority }));
      //.filter(p => !this.blockContainsPoint(to, p, 0));

    const absPointsTo: ConnectionPoint[] = to.connectionPoints
      .map(p => ({x: to.x + p.x, y: to.y + p.y, r: p.r, priority: p.priority }));
      //.filter(p => !this.blockContainsPoint(from, p, 0));

    const possiblePairs: ConnectionPointPair[] = [];
    absPointsFrom.forEach(from => {
      absPointsTo.forEach(to => {
        possiblePairs.push({from, to});
      });
    });

    const minPair = minBy(possiblePairs, pair => this.connectionPointDistance(pair.from, pair.to));
    return [minPair.from, minPair.to];
  }

  private blockContainsPoint(b: BlockRenderData, p: Point, padding: number): boolean {
    // padding enlarges the rectangle
    return p.x >= b.x - padding &&
      p.x <= b.x + padding &&
      p.y >= b.y - padding &&
      p.y <= b.y + padding;
  }

  // Computes the distance between connection points, giving preference to middle points
  private connectionPointDistance(from: ConnectionPoint, to: ConnectionPoint): number {
    const distance = this.euclideanDistance(from, to);

    // 10% bonus for every priority point
    return distance - (from.priority ? .1 * distance : 0) - (to.priority ? .1 * distance : 0);
  }

  private euclideanDistance(from: Point, to: Point): number {
    return Math.sqrt(Math.pow(to.x - from.x, 2) + Math.pow(to.y - from.y, 2));
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
  priority: boolean;
}

interface ConnectionPointPair {
  from: ConnectionPoint;
  to: ConnectionPoint;
}
