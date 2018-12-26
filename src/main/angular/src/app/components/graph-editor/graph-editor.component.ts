import {Component, ElementRef, OnInit} from '@angular/core';
import {ActionBlock, Block, StateMachine} from "../../models/StateMachine";
import {graphlib, layout} from "dagre";
import Graph = graphlib.Graph;
import * as d3 from 'd3';
import { range } from 'lodash';

type BlockSelection = d3.Selection<SVGGElement, Block, any, any>;

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
  private svg: SVGElement;

  constructor(private elementRef: ElementRef) {}

  ngOnInit(): void {
    this.svg = (this.elementRef.nativeElement as HTMLDivElement).getElementsByTagName('svg').item(0);
    this.render(this.stateMachine);
  }

  private render(stateMachine: StateMachine) {
    this.updateBlockRenderData(stateMachine);

    const graph: Graph = new graphlib.Graph();
    graph.setGraph({});
    graph.setDefaultEdgeLabel(function() { return {}; });
    stateMachine.blocks.forEach(block => {
      graph.setNode(block.id, this.blockRenderData[block.id]);
    });
    stateMachine.blocks.forEach(block => {
      (block as ActionBlock).nextBlocks.forEach(nextBlock => {
        graph.setEdge(block.id, nextBlock);
      });
    });

    layout(graph);

    const frame = d3.select(this.svg)
      .append('g');

    const blocks = frame.selectAll('g.block')
      .data(stateMachine.blocks);

    const enteringBlocks = blocks.enter()
      .append('g')
      .attr('class', 'block')
      .attr('transform', (b: Block) =>
        `translate(${this.blockRenderData[b.id].x},${this.blockRenderData[b.id].y})`)

    enteringBlocks
      .append('rect')
      .attr('class', 'block')
      .attr('width', (b: Block) => this.blockRenderData[b.id].width)
      .attr('height', (b: Block) => this.blockRenderData[b.id].height);

    this.appendHConnectionPoints(enteringBlocks, 3, 20, 5);
    this.appendVConnectionPoints(enteringBlocks, 3, 20, 5);

    enteringBlocks
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('alignment-baseline', 'middle')
      .attr('dx', (b: Block) => this.blockRenderData[b.id].width / 2)
      .attr('dy', (b: Block) => this.blockRenderData[b.id].height / 2)
      .text((b: Block) => b.name);

    console.log(this.blockRenderData);
  }

  private updateBlockRenderData(stateMachine: StateMachine) {
    stateMachine.blocks.forEach(block => {
      if (!(block.id in this.blockRenderData)) {
        this.blockRenderData[block.id] = {
          width: 100,
          height: 100,
          x: 0,
          y: 0
        };
      }
    });
  }

  private appendHConnectionPoints(d3Blocks: BlockSelection, numberOfPoints: number, padding: number, radius: number) {
    range(numberOfPoints).forEach(i => {
      this.appendConnectionPoint(d3Blocks, radius)
        .attr('cx', (b: Block) => this.pointPosition(padding, numberOfPoints, this.blockRenderData[b.id].height, i))
        .attr('cy', 0);
      this.appendConnectionPoint(d3Blocks, radius)
        .attr('cx', (b: Block) => this.pointPosition(padding, numberOfPoints, this.blockRenderData[b.id].height, i))
        .attr('cy', (b: Block) => this.blockRenderData[b.id].width);
    });
  }

  private appendVConnectionPoints(d3Blocks: BlockSelection, numberOfPoints: number, padding: number, radius: number) {
    range(numberOfPoints).forEach(i => {
      this.appendConnectionPoint(d3Blocks, radius)
        .attr('cx', 0)
        .attr('cy', (b: Block) => this.pointPosition(padding, numberOfPoints, this.blockRenderData[b.id].width, i));
      this.appendConnectionPoint(d3Blocks, radius)
        .attr('cx', (b: Block) => this.blockRenderData[b.id].height)
        .attr('cy', (b: Block) => this.pointPosition(padding, numberOfPoints, this.blockRenderData[b.id].width, i));
    });
  }

  private pointPosition(padding: number, numberOfPoints: number, totalSize: number, pointNr: number) {
    if (numberOfPoints === 1) {
      return totalSize / 2;
    }
    return padding + pointNr * (totalSize - 2 * padding) / (numberOfPoints - 1);
  }

  private appendConnectionPoint(d3Blocks: BlockSelection, radius: number): BlockSelection {
    return d3Blocks
      .append('circle')
      .attr('class', 'connection-point')
      .attr('r', radius);
  }
}

interface BlockRenderData {
  width: number;
  height: number;
  x: number;
  y: number;
}
