package visualalgol.casosdeuso;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import visualalgol.casosdeuso.desenhistas.Desenhista;
import visualalgol.entidades.Algoritmo;
import visualalgol.entidades.InstrucaoGenerica;
import visualalgol.entidades.Linha;
import visualalgol.swing.MainFrame;

public class AtualizarTela extends CasoDeUso {
	private MainFrame mainFrame;
	private boolean ligado = true;

	@Override
	public void executar(MainFrame mainFrame) {
		this.mainFrame = mainFrame;
		final Thread t = new Thread() {
			@Override
			public void run() {
				while (ligado) {
					try {
						Thread.sleep(100);
						atualizar();
					} catch (InterruptedException e) {
						// interrompido
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		t.start();
		mainFrame.addWindowListener(new WindowListener() {

			@Override
			public void windowActivated(WindowEvent e) {

			}

			@Override
			public void windowClosed(WindowEvent e) {

			}

			@Override
			public void windowClosing(WindowEvent e) {
				ligado = false;
			}

			@Override
			public void windowDeactivated(WindowEvent e) {

			}

			@Override
			public void windowDeiconified(WindowEvent e) {

			}

			@Override
			public void windowIconified(WindowEvent e) {

			}

			@Override
			public void windowOpened(WindowEvent e) {

			}

		});
	}

	private void atualizar() {
		Algoritmo algoritimo = mainFrame.getAlgoritmo();
		int w = mainFrame.getTelaDesenhoFluxograma().getWidth();
		int h = mainFrame.getTelaDesenhoFluxograma().getHeight();
		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics gra = bi.getGraphics();
		gra.setColor(Color.WHITE);
		gra.fillRect(0, 0, w, h);
		gra.setColor(Color.BLACK);
		// desenhar as linhas em baixo
		for (Linha linha : algoritimo.getListLinha()) {
			Point lastPoint = new Point(linha.getOrigem().getX(), linha.getOrigem().getY());
			for (Point point : linha.getListPontos()) {
				gra.drawLine(lastPoint.x, lastPoint.y, point.x, point.y);
				lastPoint = point;
			}
			if(linha.getDestino()!=null){
				gra.drawLine(lastPoint.x, lastPoint.y, linha.getDestino().getX(), linha.getDestino().getY());
			}else{
				if (linha.getPontoTemporario() != null && lastPoint != null) {
					Point p = linha.getPontoTemporario();
					gra.drawLine(lastPoint.x, lastPoint.y, p.x, p.y);
				}
			}
		}
		// desenhar as instrucoes em cima das linhas
		for (InstrucaoGenerica instrucao : algoritimo.getListComando()) {
			try {
				Desenhista desenhista = (Desenhista) Class.forName("visualalgol.casosdeuso.desenhistas.Desenhar" + instrucao.getClass().getSimpleName()).newInstance();
				desenhista.desenhar(instrucao, bi);
			} catch (ClassNotFoundException e) {
				System.err.println("Crie uma classe chamada: visualalgol.casosdeuso.desenhistas.Desenhar" + instrucao.getClass().getSimpleName());
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		mainFrame.getTelaDesenhoFluxograma().setIcon(new ImageIcon(bi));
	}
}