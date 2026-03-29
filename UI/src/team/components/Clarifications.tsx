import React, { useState } from 'react';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from './ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';
import { Textarea } from './ui/textarea';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './ui/table';
import { MessageSquarePlus, MessageCircle } from 'lucide-react';
import { Badge } from './ui/badge';

interface Clarification {
  id: number;
  problem: string;
  question: string;
  status: 'Pending' | 'Answered';
  reply: string;
}

const clarifications: Clarification[] = [
  {
    id: 1,
    problem: 'A',
    question: 'Is the array guaranteed to be non-empty?',
    status: 'Answered',
    reply: 'Yes, the array will always have at least one element.',
  },
  {
    id: 2,
    problem: 'C',
    question: 'What is the maximum value of N?',
    status: 'Answered',
    reply: 'N will not exceed 10^5 as stated in the constraints.',
  },
  {
    id: 3,
    problem: 'D',
    question: 'Can the graph contain self-loops?',
    status: 'Pending',
    reply: '',
  },
];

export function Clarifications() {
  const [open, setOpen] = useState(false);
  const [selectedProblem, setSelectedProblem] = useState('A');
  const [question, setQuestion] = useState('');

  const handleSubmit = () => {
    console.log('Submitting question for problem', selectedProblem);
    setOpen(false);
    setQuestion('');
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
      <div className="p-4 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
        <h3 className="text-gray-900">Clarifications</h3>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button className="bg-[#FACC15] hover:bg-[#F4C000] text-gray-900">
              <MessageSquarePlus className="w-4 h-4 mr-2" />
              New Question
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Submit a Clarification Request</DialogTitle>
            </DialogHeader>
            <div className="space-y-4 mt-4">
              <div className="space-y-2">
                <label className="text-sm text-gray-700">Related Problem</label>
                <Select value={selectedProblem} onValueChange={setSelectedProblem}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="General">General</SelectItem>
                    <SelectItem value="A">Problem A</SelectItem>
                    <SelectItem value="B">Problem B</SelectItem>
                    <SelectItem value="C">Problem C</SelectItem>
                    <SelectItem value="D">Problem D</SelectItem>
                    <SelectItem value="E">Problem E</SelectItem>
                    <SelectItem value="F">Problem F</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-700">Your Question</label>
                <Textarea
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  placeholder="Type your question here..."
                  className="min-h-[120px]"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setOpen(false)}>
                  Cancel
                </Button>
                <Button 
                  onClick={handleSubmit}
                  className="bg-[#FACC15] hover:bg-[#F4C000] text-gray-900"
                >
                  Submit Question
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </div>
      <div className="overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Problem</TableHead>
              <TableHead>Question</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Admin Reply</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {clarifications.map((clarification) => (
              <TableRow key={clarification.id}>
                <TableCell>
                  <span className="px-2 py-1 bg-gray-100 rounded">
                    {clarification.problem}
                  </span>
                </TableCell>
                <TableCell className="max-w-xs">
                  <div className="flex items-start gap-2">
                    <MessageCircle className="w-4 h-4 text-gray-400 mt-0.5 flex-shrink-0" />
                    <span className="text-gray-700">{clarification.question}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <Badge 
                    variant={clarification.status === 'Answered' ? 'default' : 'secondary'}
                    className={clarification.status === 'Answered' 
                      ? 'bg-green-100 text-green-700 hover:bg-green-100' 
                      : 'bg-yellow-100 text-yellow-700 hover:bg-yellow-100'
                    }
                  >
                    {clarification.status}
                  </Badge>
                </TableCell>
                <TableCell className="max-w-md text-gray-700">
                  {clarification.reply || (
                    <span className="text-gray-400 italic">Waiting for response...</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}