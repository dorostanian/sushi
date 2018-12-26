export interface StateMachine {
  blocks: Block[];
}

export class Block {
  public id: string;
  public name?: string;
  public type?: string;

  constructor(id: string, name: string, type: string) {
    this.id = id;
    this.name = name;
    this.type = type;
  }
}

export class ActionBlock extends Block {
  public nextBlocks: string[];

  constructor(id: string, name: string, nextBlocks: string[] = []) {
    super(id, name, 'Action');
    this.nextBlocks = nextBlocks;
  }
}

export class BranchBlock extends Block {
  public mapping: { [blockId: string]: string }


  constructor(id: string, name: string, mapping: { [p: string]: string }) {
    super(id, name, 'Branch');
    this.mapping = mapping;
  }
}
