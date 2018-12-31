import {Component, ElementRef, OnInit} from '@angular/core';
import {ActionBlock, Block, StateMachine} from "../../models/StateMachine";
import {GraphEdge, graphlib, layout} from "dagre";
import * as d3 from 'd3';
import {ContainerElement} from 'd3';
import Graph = graphlib.Graph;
import {EditorConfig} from "../../models/EditorConfig";
import {EditorConfigService} from "../../services/editor-config.service";

@Component({
  selector: 'app-graph-editor',
  templateUrl: './graph-editor.component.html',
  styleUrls: ['./graph-editor.component.scss']
})
export class GraphEditorComponent implements OnInit {
  private id = 1;
  private stateMachine: StateMachine = {
    blocks: [
      new ActionBlock('' + this.id++, 'Block 1', ['2']),
      new ActionBlock('' + this.id++, 'Block 2', ['3', '4']),
      new ActionBlock('' + this.id++, 'Block 3', []),
      new ActionBlock('' + this.id++, 'Block 4', ['5']),
      new ActionBlock('' + this.id++, 'Block 5', []),
    ]
  };

  private editorConfig: EditorConfig;

  private graph: Graph = new graphlib.Graph();

  private blockRenderData: { [blockId: string]: BlockRenderData } = {};
  private svg: d3.Selection<SVGElement, any, any, any>;
  private frame: d3.Selection<SVGGElement, any, any, any>;

  private edgeLineFn = d3.line<Point>()
    .x((d: any) => d.x + 50)
    .y((d: any) => d.y + 50)
    .curve(d3.curveMonotoneY);

  private zoom = d3.zoom()
    .scaleExtent([.5, 3])
    .on("zoom", () => this.zoomed());

  constructor(private editorConfigService: EditorConfigService, private elementRef: ElementRef) {
    editorConfigService.getEditorConfig().subscribe(config => {
      this.editorConfig = config;
    });

    editorConfigService.getAddBlockObservable().subscribe(() => {
      this.stateMachine.blocks.push(new ActionBlock('' + this.id++, 'New block'));
      this.setStateMachine(this.stateMachine);
      this.render();
    });
  }

  ngOnInit(): void {
    const svg = (this.elementRef.nativeElement as HTMLDivElement).getElementsByTagName('svg').item(0);
    this.svg = d3.select(svg);
    this.svg.call(this.zoom);
    this.frame = this.svg.append('g');

    this.graph.setGraph({});
    this.graph.setDefaultEdgeLabel(function() { return {}; });

    this.setStateMachine(this.stateMachine);
    this.render();
  }

  private setStateMachine(stateMachine: StateMachine) {
    this.stateMachine = stateMachine;
    this.updateBlockRenderData(this.stateMachine);
  }

  private render() {
    this.stateMachine.blocks.forEach(block => {
      this.graph.setNode(block.id, this.blockRenderData[block.id]);
    });
    this.stateMachine.blocks.forEach(block => {
      (block as ActionBlock).nextBlocks.forEach(nextBlock => {
        this.graph.setEdge(block.id, nextBlock);
      });
    });

    layout(this.graph);

    const edges = this.graph.edges().map(e => this.graph.edge(e));
    this.frame.selectAll('path.edge')
      .data(edges)
      .enter()
      .append('path')
      .attr('class', 'edge')
      .attr('d', (edge: GraphEdge) => this.edgeLineFn(edge.points))
      .attr('marker-end', 'url(#edge-arrow-tip)');

    const blockRenderDatas: BlockRenderData[] = this.graph.nodes()
      .map(n => this.blockRenderData[n])
      .filter(d => d !== undefined && d !== null);

    const blocks = this.frame.selectAll('g.block')
      .data(blockRenderDatas);


    const enteringBlocks = blocks.enter()
      .append('g')
      .attr('class', 'block');

    enteringBlocks
      .append('rect')
      .attr('class', 'block')
      .attr('width', b => b.width)
      .attr('height', b => b.height);

    enteringBlocks
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('alignment-baseline', 'middle')
      .attr('dx', b => b.width / 2)
      .attr('dy', b => b.height / 2)
      .text((b: Block) => b.name);

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

  private updateBlockRenderData(stateMachine: StateMachine) {
    stateMachine.blocks.forEach(block => {
      if (!(block.id in this.blockRenderData)) {
        this.blockRenderData[block.id] = {
          id: block.id,
          name: block.name,
          width: 100,
          height: 100,
          x: 0,
          y: 0
        };
      }
    });

    // TODO: Remove blocks from render data that are not in the state machine
  }

  private mouse(): Point {
    const mouse = d3.mouse(this.svg.node() as ContainerElement);
    return { x: mouse[0], y: mouse[1] };
  }
}

interface BlockRenderData {
  id: string;
  name?: string;
  width: number;
  height: number;
  x: number;
  y: number;
}

interface Point {
  x: number;
  y: number;
}
